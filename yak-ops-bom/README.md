# Introduction

The `yak-ops-bom` module is used to manage the version of third part dependencies. If you want to import
`yak-ops-xx` to your project, you need to import `yak-ops-bom` together by below way,
this can help you to manage the version.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.yak.ops</groupId>
            <artifactId>yak-ops-bom</artifactId>
            <version>${yak-ops.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

If you want to override the version defined in `yak-ops-bom` you can directly add the version at your
module's `dependencyManagement`.
