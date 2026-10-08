# Full Portal Build

Runs `ant all`, since no Gradle deploy builds portal core. **Per-Module Compile** also hands off to it when its deploy set grows past the point where one full build is cheaper.

## Match

`^(portal-impl|portal-kernel|portal-test|portal-web|support-tomcat|util-bridges|util-java|util-slf4j|util-taglib)/ &! ^portal-web/test/|\.properties$`

## Command

```bash
ant all -Dgradle.stop.daemon.enabled=false
```

`ant all` is `clean` + `compile` + `deploy`; the deploy target's marketplace branch deploys every project with a `.lfrbuild-portal` marker.

## Notes

When this fires, **Per-Module Compile** still runs for any modules in the touched set without a `.lfrbuild-portal` marker (`ant all`'s marketplace branch only deploys modules with the marker), and **Integration Test Compile** is obviated for `.lfrbuild-portal` modules. Both assume the build succeeded. A Full Portal Build that failed produced no compile signal, so it obviates nothing, and everything it would have covered still needs its own run.

## Time Estimate

~8 min.