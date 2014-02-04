osgi-liquibase-bundle
=====================

A Liquibase bundle that contains extensions from Everit so that
Liquibase can be used in an OSGi environmnent. When this bundle is used, 
no other jar of Liquibase should be dropped into the OSGi container.

## Inclusion of changeLog from other bundles

One enhancement of this bundle is that in XML changeLog format it is
possible to address changeLogs from other bundles.

To be able to use the OSGi include element, the OSGiResourceAccessor class
has to be instantiated and passed to the Liquibase object. By default,
resources will be searched in the bundle containing the changeLog file. 
However, it is possible to prefix the file path with an "eosgi:"
prefix. In that case, the parser will search for Bundle wires based on
a special capability and if it finds an appropriate wire, a bundle switch will
be carried out during the parsing process. For example, let`s see a case where 
an include tag is defined in the following way:

```xml
<include file="eosgi:myApp" />
```

In this case, the current bundle containing this snippet has to be wired
to another bundle based on the following capability:

```
liquibase.schema;name=myApp;resource=/pathToChangelogFile
```

Additional filters may be defined in include tags like this:

```xml
<include file="eosgi:myApp;filter:=(version>=2)" />
```

In this case, the provider bundle will contain something like this in the
MANIFEST file:

```
Provide-Capability: liquibase.schema;name=myApp;resource=/path;version=3 
```

Furthermore, the bundle that contains the inclusion must contain a requirement:

```
Require-Capability: liquibase.schema;filter:=(name=myApp)
```

Please note, that the resource attribute on the provider side is always
required! The parser will search for the changelog file based on that
attribute.

## Future plans

In the future, it is possible that there will be a maven plugin that
automatically generates Require-Capability entries in MANIFEST
if it finds a changelog file in the bundle.
