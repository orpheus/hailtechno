#!/bin/bash

dbuild() {
  docker build . -t hailtechno$1
}

drun() {
  docker run --network="host" hailtechno$1
}
