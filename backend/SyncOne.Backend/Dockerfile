FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 80
EXPOSE 443

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY ["backend/SyncOne.Backend/SyncOne.Backend.csproj", "backend/SyncOne.Backend/"]
COPY ["backend/SyncOne.Core/SyncOne.Core.csproj", "backend/SyncOne.Core/"]
COPY ["backend/SyncOne.Infrastructure/SyncOne.Infrastructure.csproj", "backend/SyncOne.Infrastructure/"]
RUN dotnet restore "backend/SyncOne.Backend/SyncOne.Backend.csproj"
COPY . .
WORKDIR "/src/backend/SyncOne.Backend"
RUN dotnet build "SyncOne.Backend.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "SyncOne.Backend.csproj" -c Release -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "SyncOne.Backend.dll"]
