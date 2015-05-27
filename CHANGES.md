# Durian releases

### Version 3.0 - TBD

* Merged `GetterSetter` and `Box` into just `Box`.
* Rather than `Box` and `Box.NonNull extends Box`, we now have `Box` and `Box.Nullable`.
	+ Non-null by default is much better.
	+ There should not be an inheritance hierarchy between the nullable and non-null versions, and now there isn't.

### Version 2.0 - May 13th 2015 ([jcenter](https://bintray.com/diffplug/opensource/spotless/2.0/view))

* Renamed ErrorHandler to Errors.  This was done mainly to avoid name conflicts with the many other ErrorHandler classes that are out in the wild, but it also has the advantage of being shorter.

### Version 1.0.1 - May 13th 2015 ([jcenter](https://bintray.com/diffplug/opensource/spotless/1.0.1/view))

* The Maven POM was missing the FindBugs annotations, which was causing compile warnings for users of the library.  It now includes them under the proper 'provided' scope.

### Version 1.0 - May 13th 2015 ([jcenter](https://bintray.com/diffplug/opensource/durian/1.0/view))

* First stable release.

### Version 0.1 - April 20th 2015 ([jcenter](https://bintray.com/diffplug/opensource/durian/0.1/view))

* First release, to test out that we can release to jcenter and whatnot.