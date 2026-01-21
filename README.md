1. netstat -ano | findstr 11434
2. taskkill /PID 38464 /F
3. docker compose down -v
4. docker compose up -d --build
5. ollama run llama3
6. ollama pull mxbai-embed-large
7. ollama pull llama3
8. ollama list
9. ollama rm gemma3:12b
10. docker build -t askhr-backend ./backend/AskHr
11. docker run -d --name askhr-backend --network askhr-network -p 9091:9091 -e SPRING_PROFILES_ACTIVE=docker -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 -v h2_data:/data askhr-backend
12. docker run -d --name qdrant --network askhr-network -p 6333:6333 -p 6334:6334 -v qdrant_data:/qdrant/storage qdrant/qdrant   -- if you want in netwwork
13. docker run -d --name ollama -p 11434:11434 -v ollama_data:/root/.ollama ollama/ollama:latest
14. docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v qdrant_data:/qdrant/storage qdrant/qdrant:latest
15. docker run -d --name askhr-backend --network askhr-network -p 9091:9091 -e SPRING_PROFILES_ACTIVE=docker -e SPRING_DATASOURCE_URL=jdbc:h2:file:/data/askhrdb -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver -e SPRING_DATASOURCE_USERNAME=sa -e SPRING_DATASOURCE_PASSWORD= -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 -v h2_data:/data askhr-backend
===============
16. curl -X PUT "http://localhost:6333/collections/vector_store" -H "Content-Type: application/json" -d "{\"vectors\":{\"size\":1024,\"distance\":\"Cosine\"}}"
17. curl http://localhost:6333/collections
    // it should return ---   {"result":{"collections":[{"name":"vector_store"}]},"status":"ok","time":0.000065426}

