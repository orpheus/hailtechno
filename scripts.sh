#!/bin/bash

dbuild() {
  lein do clean, uberjar
  docker build . -t hailtechno/hailtechno$1
}

drun() {
  docker run --network="host" hailtechno/hailtechno$1
}

ddeploy() {
  kubectl delete deployment hailtechno
  dbuild
  docker push hailtechno/hailtechno:latest
  kubectl apply -k deployments/templates/
}

