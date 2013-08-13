<div align="center"><img src="https://github.com/ayamada/clan/raw/master/doc/img/logo_b.png" /><br /><a href="https://travis-ci.org/ayamada/clan"><img src="https://travis-ci.org/ayamada/clan.png?branch=master" alt="Build Status" /></a><br /><strong>CLAN : Clojure, LibGDX, Android, Neko application construct kit</strong><br /><strong>CLAN : Clojure, LibGDX, Android, Neko アプリケーション構築キット</strong></div>

# CLAN

CLAN supply to an environment that construct application for Android using [libgdx](http://libgdx.badlogicgames.com/) by only clojure code. You must understand to [clojure](http://clojure.org/) and [libgdx](http://libgdx.badlogicgames.com/).

It can construct application for not only Android but also Windows and other desktop OS at a time (but it need install java runtime to work).

See document on http://doc.tir.ne.jp/devel/clan .

Version [0.1.0](https://github.com/ayamada/clan/tree/0.1.0) is latest, but not released yet.

Version [0.0.6](https://github.com/ayamada/clan/tree/0.0.6) is old stable(use maven, without lein).

* * * * *

これは「clojureのコード書くだけでlibgdx使ったandroidアプリができた！」を 実現する為のアプリ構築キットです。 別途、 [clojure](http://clojure.org/) と [libgdx](http://libgdx.badlogicgames.com/) の知識が必要です。

androidアプリだけではなく、windowsとその他のデスクトップos向けアプリも 同時に構築できます(ただし実行にはjavaインストールが必要)。

詳細は http://doc.tir.ne.jp/devel/clan のドキュメントを見てください。

現在の最新版は [0.1.0](https://github.com/ayamada/clan/tree/0.1.0) ですが、まだ未リリース状態です。

古い安定版(maven使用、leinなし)は [0.0.6](https://github.com/ayamada/clan/tree/0.0.6) です。

ChangeLog
---------

- [ChangeLog](ChangeLog)

Link
----

-   [Clojure](http://clojure.org/)

    -   [CheatSheet](http://clojure.org/cheatsheet)
    -   [ClojureDocs](http://clojuredocs.org/)
    -   [Clojure for Android](https://github.com/clojure-android/clojure)

        -   [Deep Blue Lambda](http://www.deepbluelambda.org/): Clojure for Android のメンテナの人のサイト。nekoのオリジナル版もこの人が作った。
        -   [Clojure REPL](https://play.google.com/store/apps/details?id=com.sattvik.clojure_repl): この人が作った、android用clojureのREPLコンソール。一行コンソールからだけではなく、PCのVimClojure経由での遠隔操作もできる。android実機の環境で「○○クラスは標準で入ってるのか？」みたいな調べ物をするのに便利。

-   [LibGDX](http://libgdx.badlogicgames.com/)

    -   [github repository](https://github.com/libgdx/libgdx)
    -   [libgdx API](http://libgdx.badlogicgames.com/nightlies/docs/api/overview-summary.html)
    -   [Wiki](http://code.google.com/p/libgdx/wiki/TableOfContents)
    -   [Androidゲームプログラミング A to Z](http://www.impressjapan.jp/books/3113)([amazon](http://www.amazon.co.jp/o/ASIN/4844331132/tirnejp-22)): [Beginning Android Games](http://www.apress.com/9781430230427)(LibGDXのメンテナの人が著者)の日本語訳版。良書。ただし本家の方は[2nd Edition](http://www.apress.com/9781430246770)が出てるが日本語訳版は初版ベース。
    -   [libgdxメモ](http://doc.tir.ne.jp/devel/clan/libgdx): ayamadaがメモしたlibgdxのノウハウ集。

-   [Android](http://developer.android.com/)

    -   [ProGuard](http://proguard.sourceforge.net/), [ProGuard(Android Developers)](http://developer.android.com/tools/help/proguard.html)([日本語訳](http://www.techdoctranslator.com/android/developing/tools/proguard))

-   [Neko(forked by Alexander Yakushev)](https://github.com/alexander-yakushev/neko)

    -   [reference](http://alexander-yakushev.github.com/neko/)
    -   [Clojure Development on Android](http://clojure-android.blogspot.com/): nekoのメンテナの人の解説ブログ。

-   [launch4j](http://launch4j.sourceforge.net/): javaアプリをwindowsのexe形式にしてくれるアプリ。ただしexe形式にしても実行にはjavaインストールが必須。launch4jでのexe生成自体は非windows環境でも動く。

-   [lein-droid](https://github.com/clojure-android/lein-droid): leinでandroidアプリを生成する為のleinプラグイン。開発中はapkにnREPLサーバ機能を自動で組み込んで、android実機の外からプロセスにREPL接続できるようにする機能あり。

-   [clojurelibgdx](https://github.com/thomas-villagers/clojurelibgdx): 類似プロジェクト。

License
-------

CLAN have [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Which can be found in the file [LICENSE](LICENSE).

Apps were built on CLAN
-----------------------

I'm glad, if you made apps by CLAN, and inform that to [@rnkv(=ayamada)](https://twitter.com/rnkv). I will append to this list.

(もしあなたがCLANを使って何かアプリを作ったら、 [@rnkv](https://twitter.com/rnkv) まで教えてもらえると嬉しいです。このリストに追加します。)

* * * * *

-   http://vnctst.tir.jp/ja/games/driftcat_underworld.html

    -   A short game that the cat want to return to home from empire of underworld. That were made by ayamada. (sorry, this app is required to read japanese.)

    -   猫を操作して地底帝国からの脱出を目指す、ぬるいRPGです。 ayamada作。

-   http://vnctst.tir.jp/ja/games/space_drop.html

    -   Packaged version of bundled sample app 'space drop'. That were made by ayamada. (sorry, this page written in japanese.)

    -   同梱のサンプルアプリ'space drop'のパッケージ版です。 ayamada作。


