# Netty Plus

### 使用方式

修改本地Maven配置文件settings.xml

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <activeProfiles>
    <activeProfile>github</activeProfile>
  </activeProfiles>
  <profiles>
    <profile>
      <id>github</id>
      <repositories>
        <repository>
                  <id>central</id>
                  <url>https://repo1.maven.org/maven2</url>
                  <releases><enabled>true</enabled></releases>
                  <snapshots><enabled>true</enabled></snapshots>
                </repository>
        <repository>
          <id>github</id>
          <name>Caster Netty Plus Maven Packages</name>
          <url>https://maven.pkg.github.com/CasterWx/netty-plus</url>
        </repository>
      </repositories>
    </profile>
  </profiles>

  <servers>
    <server>
      <id>github</id>
      <username>填写你的Github用户名</username>
      <password>填写你的Github token</password>
    </server>
  </servers>
</settings>
```

引入Netty Plus依赖

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-plus</artifactId>
    <version>4.1.41.Final</version>
</dependency>
```

执行`mvn install`即可。