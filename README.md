# rest-maven-plugin 

***froked from https://github.com/cjnygard/rest-maven-plugin***

Welcome to the rest-maven-plugin plugin for Apache Maven 3.

This plugin is meant to provide an easy way to convert java files to script instance json format and deploy it into opencell via REST URL.

## Getting started with the plugin :

To use this plugin and start working with the rest request, in the pom.xml :

* add properties 
```xml
 <properties>
        <opencell.url>http://localhost:8080</opencell.url>
        <!--opencell's username:password encoded with base64 -->
        <opencell.authorization>b3BlbmNlbGwuc3VwZXJhZG1pbjpvcGVuY2VsbC5zdXBlcmFkbWlu</opencell.authorization>
        <rest-maven-plugin.version>0.1.6</rest-maven-plugin.version>
        <opencell.path>/opencell/api/rest/scriptInstance/createOrUpdate</opencell.path>
    </properties>
```

* delcate a profile
```xml
<profile>
	<id>deploy-script</id>
	<build>
		<plugins>
			<plugin>
				<groupId>com.opencellsoft.plugins</groupId>
				<artifactId>rest-maven-plugin</artifactId>
				<version>${rest-maven-plugin.version}</version>
				<configuration>
					<endpoint>${opencell.url}</endpoint>
					<resource>${opencell.path}</resource>
					<method>POST</method>
					<saveResponse>false</saveResponse>
					<inputDir>${project.basedir}/src/main/java/com/opencellsoft/service/engie/notif</inputDir>
					<javaFile>${project.basedir}/src/main/java/org/meveo/service/script/DeleteCustomersScript.java</javaFile>
					<outputDir>${project.build.directory}/scripts/</outputDir>
					<headers>
						<Authorization>Basic ${opencell.authorization}</Authorization>
					</headers>
					<requestType>
						<type>application</type>
						<subtype>json</subtype>
					</requestType>
					<fileset>
						<directory>${project.build.directory}/scripts/</directory>
						<includes>
							<include>*.json</include>
						</includes>
					</fileset>
				</configuration>
				<dependencies>
					<dependency>
						<groupId>com.opencellsoft.plugins</groupId>
						<artifactId>rest-maven-plugin</artifactId>
						<version>${rest-maven-plugin.version}</version>
					</dependency>
				</dependencies>
			</plugin>
		</plugins>
	</build>
</profile>
```

* run this command :
`mvn rest:rest-request -P  deploy-script`

## Plugin configuration : 
### Adding java class or package 

To specify the input package or class you can add the following configuration: 

* To use a single java file:
`mvn rest:rest-request -P  deploy-script -DjavaFile=/src/main/java/org/meveo/service/script/DeleteCustomersScript.java`

* To use a package recursively:
`mvn rest:rest-request -P  deploy-script -DinputDir=/src/main/java/org/meveo/service/script/`

