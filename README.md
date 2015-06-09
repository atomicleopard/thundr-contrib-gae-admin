thundr-gae-admin
=================

A drop in datastore admin console for thundr-gae apps.

* Query data in the datastore
* Re-generate search indexes.

## Getting started

1. Add the Maven dependency to your pom.xml:
```
<dependency>
  <groupId>com.atomicleopard.thundr</groupId>
  <artifactId>thundr-contrib-gae-admin</artifactId>
  <version>1.0.0</version>
</dependency>
```

2. Add the Thundr module dependency to your Thundr Application Module:
```
	@Override
	public void requires(DependencyRegistry dependencyRegistry) {
		dependencyRegistry.addDependency(GaeAdminModule.class);
	}
```

### Advanced

* By default, the GaeAdminModule will scan your injection context and find your any Repository implementations you have to allow search index regeneration. You can switch this off and register your repositories manually:
```
GaeAdminModule.disableAutoRegister();
GaeAdminModule.register(MyRepository.class);
```

* By default, the GAE Admin Module will be available at this url `/admin/datastore`. However, you can override this with:
```
GaeAdminModule.setBaseUrl("/my/custom/url");
```

