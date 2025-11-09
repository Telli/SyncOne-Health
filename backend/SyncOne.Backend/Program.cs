using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.SemanticKernel;
using Serilog;
using SyncOne.Core.Agents;
using SyncOne.Core.Services;
using SyncOne.Infrastructure.Data;
using SyncOne.Infrastructure.Services;
using System.Text;
using System.Threading.RateLimiting;

var builder = WebApplication.CreateBuilder(args);

// Logging
Log.Logger = new LoggerConfiguration()
    .ReadFrom.Configuration(builder.Configuration)
    .CreateLogger();

builder.Host.UseSerilog();

// Database
builder.Services.AddDbContext<ApplicationDbContext>(options =>
    options.UseNpgsql(
        builder.Configuration.GetConnectionString("DefaultConnection"),
        npgsqlOptions => npgsqlOptions.UseVector()));

// HTTP Clients
builder.Services.AddHttpClient<IMedGemmaService, MedGemmaService>();

builder.Services.AddHttpClient("Embeddings", client =>
{
    client.BaseAddress = new Uri(builder.Configuration["Embeddings:Endpoint"] ?? "http://localhost:8000");
    var apiKey = builder.Configuration["Embeddings:ApiKey"];
    if (!string.IsNullOrEmpty(apiKey))
    {
        client.DefaultRequestHeaders.Add("Authorization", $"Bearer {apiKey}");
    }
});

builder.Services.AddHttpClient("FCM", client =>
{
    client.BaseAddress = new Uri("https://fcm.googleapis.com");
    var serverKey = builder.Configuration["FCM:ServerKey"];
    if (!string.IsNullOrEmpty(serverKey))
    {
        client.DefaultRequestHeaders.Add("Authorization", $"key={serverKey}");
    }
});

builder.Services.AddHttpClient("Twilio");

// Repositories
builder.Services.AddScoped<SyncOne.Infrastructure.Repositories.AuditLogRepository>();

// Services
builder.Services.AddScoped<IRagService, RagService>();
builder.Services.AddScoped<IAlertService, AlertDispatchService>();
builder.Services.AddScoped<IFeedbackService, FeedbackStorageService>();
builder.Services.AddSingleton<RemoteWipeService>();

// Optional services (only registered if configured)
if (!string.IsNullOrEmpty(builder.Configuration["Twilio:AccountSid"]))
{
    builder.Services.AddScoped<TwilioSmsService>();
}

if (!string.IsNullOrEmpty(builder.Configuration["FCM:ServerKey"]))
{
    builder.Services.AddScoped<FirebaseCloudMessagingService>();
}

// Semantic Kernel for AI agents
builder.Services.AddSingleton(sp =>
{
    var kernel = Kernel.CreateBuilder()
        .AddOpenAIChatCompletion(
            modelId: builder.Configuration["OpenAI:ModelId"] ?? "gpt-4",
            apiKey: builder.Configuration["OpenAI:ApiKey"] ?? "sk-test")
        .Build();
    return kernel;
});

// Agents
builder.Services.AddScoped<PrimaryCareAgent>();
builder.Services.AddScoped<MaternalHealthAgent>();
builder.Services.AddScoped<RxSafetyAgent>();
builder.Services.AddScoped<ReferralAgent>();
builder.Services.AddScoped<IAgent, CoordinatorAgent>(sp =>
{
    var specialists = new IAgent[]
    {
        sp.GetRequiredService<PrimaryCareAgent>(),
        sp.GetRequiredService<MaternalHealthAgent>(),
        sp.GetRequiredService<RxSafetyAgent>(),
        sp.GetRequiredService<ReferralAgent>()
    };

    return new CoordinatorAgent(
        sp.GetRequiredService<Kernel>(),
        specialists,
        sp.GetRequiredService<ILogger<CoordinatorAgent>>());
});

// Rate limiting
builder.Services.AddRateLimiter(options =>
{
    options.AddFixedWindowLimiter("inference", opt =>
    {
        opt.PermitLimit = 100;
        opt.Window = TimeSpan.FromMinutes(1);
        opt.QueueProcessingOrder = QueueProcessingOrder.OldestFirst;
        opt.QueueLimit = 10;
    });
});

// CORS
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowAndroid", policy =>
    {
        var allowedOrigins = builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>()
            ?? new[] { "http://localhost:8081" };
        policy.WithOrigins(allowedOrigins)
            .AllowAnyHeader()
            .AllowAnyMethod();
    });
});

// Authentication & Authorization
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = builder.Configuration["Jwt:Issuer"] ?? "SyncOne",
            ValidAudience = builder.Configuration["Jwt:Audience"] ?? "SyncOne",
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(builder.Configuration["Jwt:Key"] ?? "default-key-change-in-production-min-32-chars"))
        };
    });

builder.Services.AddAuthorization();

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// Middleware
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();
app.UseCors("AllowAndroid");
app.UseRateLimiter();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();

// Database migration
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
    await db.Database.MigrateAsync();
}

app.Run();
