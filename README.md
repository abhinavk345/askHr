>netstat -ano | findstr 11434
>taskkill /PID 38464 /F  
>docker compose down -v 
>docker compose up -d --build
>>>>>>>>>>>>>>>>>>>>>>
>> ollama run llama3   
>> ollama pull llama3
>> ollama list
>> ollama rm gemma3:12b
>>>>>>>>>>>>>>>>>>>>>>
>docker build -t askhr-backend ./backend/AskHr 
>>docker run -d --name askhr-backend --network askhr-network -p 9091:9091 -e SPRING_PROFILES_ACTIVE=docker -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 -v h2_data:/data askhr-backend 
>docker run -d --name qdrant --network askhr-network -p 6333:6333 -p 6334:6334 -v qdrant_data:/qdrant/storage qdrant/qdrant   -- if you want in netwwork
>docker run -d --name ollama -p 11434:11434 -v ollama_data:/root/.ollama ollama/ollama:latest
>>docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v qdrant_data:/qdrant/storage qdrant/qdrant:latest
>>
>>docker run -d --name askhr-backend --network askhr-network -p 9091:9091 -e SPRING_PROFILES_ACTIVE=docker -e SPRING_DATASOURCE_URL=jdbc:h2:file:/data/askhrdb -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver -e SPRING_DATASOURCE_USERNAME=sa -e SPRING_DATASOURCE_PASSWORD= -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 -v h2_data:/data askhr-backend
>>
>>
