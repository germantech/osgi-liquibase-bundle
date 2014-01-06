osgi-liquibase-bundle
=====================

Liquibase bundle that contains extensions from Everit to be able to use
Liquibase in an OSGi environmnent. When this bundle is used, no other jar
of Liquibase should be dropped into the OSGi container.

## Inclusion of changeLog from other bundles

One enhancement of this bundle is that in the XML changeLog format it is
possible to address changeLogs from other bundles.

To be able to use the OSGi include element, the OSGiResourceAccessor class
has to be instantiated and passed to the Liquibase object. By default
resources will be searched in the same bundle as the changeLog file is
placed. However, it is possible to prefix the file path with a "eosgi:"
prefix. In that case, the parser will search for Bundle wires based on
a special capability and if it finds a good wire, a bundle switch will
be done during the parsing process. For example, imagine that the include
tag is defined in the following way:

```xml
<include file="eosgi:myApp" />
```

In this case the current bundle, that contains this snippet, has to wire
to another bundle based on the following capability:

```
liquibase.schema;name=myApp;resource=/pathToChangelogFile
```

Additional filter may be defined in the include tag like this:

```xml
<include file="eosgi:myApp;filter:=(version>=2)" />
```

In that case the provider bundle will contain something like this in the
MANIFEST file:

```
Provide-Capability: liquibase.schema;name=myApp;resource=/path;version=3 
```

Please note, that the resource attribute is always required! The parser will
search for the changelog file based on that attribute. To have the wire, the
bundle, that contains the changeLog inclusion, must have a
Require-Capability in the MANIFEST.

In the future, it might be possible that there will be a maven plugin that
automatically creates the Require-Capability in the MANIFEST if it finds a
changelog file in the bundle.
