# apk-parser (vendored)

`app/src/main/java/net/dongliu/apk/parser` is a copy of https://github.com/zacharee/apk-parser
(commit 7cd66d8, itself a fork of hsiafan/apk-parser, BSD-2-Clause, see apk-parser-LICENSE.txt).

Local changes: `ResourceTableParser`, `Type`, `TypeHeader`, `ResourceEntry` now understand the
resource table encodings used by recent aapt2 versions (sparse types, 16 bit offsets, compact
entries). Without this, big/new apps such as the Google apps are parsed incorrectly and the
parser crashes with `ArrayIndexOutOfBoundsException` in `StringPool.get`.
