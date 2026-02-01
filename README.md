# popp-smartphone-konnektor 

## Development

Compiling and running on a dev machine:

```bash
./mvn-install.sh
cd server
./mvn-quarkus-dev.sh
```

## Testing in minikube

```bash
# Point to Minikube's Docker
eval $(minikube docker-env)

# Build image
mvn clean package

# Verify image exists in Minikube
minikube ssh docker images | grep popp-smartphone-konnektor

# Delete and recreate pod (Delete not needed if created first time)
kubectl delete -f kubernetes.yaml
kubectl apply -f kubernetes.yaml

# Access
minikube service popp-smartphone-konnektor --url
```
