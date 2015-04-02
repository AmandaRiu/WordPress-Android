# WordPress-Editor-Android #

[![Build Status](https://travis-ci.org/wordpress-mobile/WordPress-Editor-Android.svg?branch=develop)](https://travis-ci.org/wordpress-mobile/WordPress-Editor-Android)

## Introduction ##

WordPress-Editor-Android is the text editor used in the [WordPress Android app](https://github.com/wordpress-mobile/WordPress-Android) to create and edit pages & posts. In short it's a simple, straightforward way to visually edit HTML.

## Build Instructions ##

Post-checkout instructions for Windows, necessary to convert the assets symlink to a Windows symlink:

From git bash, inside the cloned project root:

    $ rm WordPressEditor/src/main/assets
    $ git ls-files --deleted -z | git update-index --assume-unchanged -z --stdin

Then, from a Windows command prompt:

    mklink /D [PROJECT_ROOT]\WordPressEditor\src\main\assets %PROJECT_ROOT%\libs\editor-common\assets

Finally, update `[PROJECT_ROOT]\.git\info\exclude` to ignore the symlink locally:

    # assets symlink
    WordPressEditor/src/main/assets

## LICENSE ##

This library is licensed under [MIT](LICENSE-MIT).
