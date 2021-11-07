#!/bin/bash

dbuild() {
  docker build . -t hailtechno/hailtechno$1
}

drun() {
  docker run --network="host" hailtechno/hailtechno$1
}
