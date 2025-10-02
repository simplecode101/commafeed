./mvnw -pl commafeed-server spotless:apply
./mvnw clean package -DskipTests -Pmysql
cd docker
rm cf.tar 
rm -rf commafeed-5.11.1-mysql
rm -rf commafeed-5.11.1-mysql-jvm.zip 
mv ../commafeed-server/target/commafeed-5.11.1-mysql-jvm.zip .
unzip commafeed-5.11.1-mysql-jvm.zip 
sudo docker build . -t cf-mysql-ai
sudo docker save cf-mysql-ai -o cf.tar
sudo scp ./cf.tar jiangcy@192.168.1.138:/home/jiangcy/servers