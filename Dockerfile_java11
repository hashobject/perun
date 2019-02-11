FROM clojure:openjdk-11-boot
ADD . /usr/src/perun
WORKDIR /usr/src/perun
RUN boot build
WORKDIR /usr/src/app
CMD ["boot","build"]
