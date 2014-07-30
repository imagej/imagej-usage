ImageJ Usage Statistics
-----------------------

This plugin collects anonymized usage statistics for known ImageJ modules
(commands, scripts, etc.).

Statistics are accumulated every time a module is executed. Once per hour,
and when ImageJ shuts down, the statistics are flushed (i.e., uploaded) to
the [ImageJ usage server](http://usage.imagej.net/) with an anonymous but
unique user/machine identifier. It is not possible to tell who a user is
from the identifier, but each user/machine combination is guaranteed to have
a unique identifier string, to facilitate computation of statistics such as:
"How many people used such-and-such command within the past 12 months?"

Collection of usage statistics is optional, and can be toggled in the
Edit > Options > Privacy... options dialog.

Such information is very useful to the developers in prioritizing bug-fixes
and new features, as well for continued funding of valuable functionality.

For further details, see [Usage](http://imagej.net/Usage) on the ImageJ web
site.
