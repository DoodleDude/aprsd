FROM ubuntu
RUN apt update
RUN apt install -y \
apt-utils
RUN apt install -y \
make
RUN DEBIAN_FRONTEND="noninteractive" apt install -y \
debhelper gettext-base \
libgettext-commons-java \
libcommons-codec-java \
libjackson2-core-java \
libjackson2-databind-java \
openjdk-11-jdk \
scala \
byacc-j \
jflex \
librxtx-java
RUN apt install -y \
openjdk-11-jre-headless \
libslf4j-java \
librxtx-java \
scala-library \
scala-xml \
scala-parser-combinators \
gettext-base \
libgettext-commons-java \
libcommons-codec-java \
libjackson2-core-java \
libjackson2-databind-java \
adduser \
apache2-utils \
sudo \
lsb-base
RUN ls
COPY . /app
WORKDIR /app
EXPOSE 8081
RUN JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF8" make
RUN make install
CMD polaric-aprsd-start