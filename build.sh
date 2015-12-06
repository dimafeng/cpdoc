lein clean
lein uberjar
docker build -t dimafeng/cpdoc .
docker push dimafeng/cpdoc