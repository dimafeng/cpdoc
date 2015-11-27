FROM java:8-jre

ADD target/cpdoc.jar /app/

WORKDIR /app

CMD ["java", "-jar", "cpdoc.jar", "-i", "/opt/input/", "-o","/opt/output/"]