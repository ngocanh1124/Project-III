FaceSDK (Luxand) installation instructions

If you need the Luxand FaceSDK (`FSDK.jar`), follow one of these options:

Option A — Install to local Maven repository (recommended)
1. Place the `FSDK.jar` file somewhere on your machine, e.g. `C:\libs\FSDK.jar`.
2. Run the following command in PowerShell (adjust path and version as needed):

```powershell
mvn install:install-file -Dfile="C:\libs\FSDK.jar" -DgroupId=com.luxand -DartifactId=facesdk -Dversion=9.7.0 -Dpackaging=jar
```

3. Add the dependency to `pom.xml` (normal dependency):

```xml
<dependency>
  <groupId>com.luxand</groupId>
  <artifactId>facesdk</artifactId>
  <version>9.7.0</version>
</dependency>
```

Option B — Keep as system-scoped JAR (not recommended)
1. Create a `libs` folder in the project root and copy `FSDK.jar` there.
2. Add a system-scoped dependency to `pom.xml` like:

```xml
<dependency>
  <groupId>com.luxand</groupId>
  <artifactId>facesdk</artifactId>
  <version>9.7.0</version>
  <scope>system</scope>
  <systemPath>${project.basedir}/libs/FSDK.jar</systemPath>
</dependency>
```

Notes
- Option A is preferable because it integrates with Maven and IDEs cleanly.
- After installing the JAR to local repo, run `mvn -U -DskipTests validate` and refresh your IDE Maven projects.
- If you want, provide the path to your `FSDK.jar` and I can run the `mvn install:install-file` command for you now.
