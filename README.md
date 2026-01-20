1. netstat -ano | findstr 11434
2. taskkill /PID 38464 /F
3. docker compose down -v
4. docker compose up -d --build
5. ollama run llama3
6. ollama pull llama3
7. ollama list
8. ollama rm gemma3:12b
9. docker build -t askhr-backend ./backend/AskHr
10. docker run -d --name askhr-backend --network askhr-network -p 9091:9091 -e SPRING_PROFILES_ACTIVE=docker -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 -v h2_data:/data askhr-backend
11. docker run -d --name qdrant --network askhr-network -p 6333:6333 -p 6334:6334 -v qdrant_data:/qdrant/storage qdrant/qdrant   -- if you want in netwwork
12. docker run -d --name ollama -p 11434:11434 -v ollama_data:/root/.ollama ollama/ollama:latest
13. docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v qdrant_data:/qdrant/storage qdrant/qdrant:latest
14. docker run -d --name askhr-backend --network askhr-network -p 9091:9091 -e SPRING_PROFILES_ACTIVE=docker -e SPRING_DATASOURCE_URL=jdbc:h2:file:/data/askhrdb -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver -e SPRING_DATASOURCE_USERNAME=sa -e SPRING_DATASOURCE_PASSWORD= -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 -v h2_data:/data askhr-backend

15. 

