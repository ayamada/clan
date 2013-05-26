.. image:: https://github.com/ayamada/clan/raw/master/doc/img/logo_b.png

CLAN : Clojure, LibGDX, Android, Neko application construct kit
===============================================================

CLAN supply to an environment that construct android application using
libgdx by only clojure code. You must understand to clojure and libgdx.

It can construct application for not only android but also
windows and other desktop OS at a time
(but it need install java runtime to work).

See document http://doc.tir.ne.jp/devel/clan .

Version `0.0.6 <https://github.com/ayamada/clan/tree/0.0.6>`_ is latest stable.

Currently, `use_lein <https://github.com/ayamada/clan/tree/use_lein>`_
branch is developed.

--------------

**CLAN : Clojure, LibGDX, Android, Neko アプリケーション構築キット**

これは「clojureのコード書くだけでlibgdx使ったandroidアプリができた！」を
実現する為のアプリ構築キットです。
別途、clojureとlibgdxの知識が必要です。

androidアプリだけではなく、windowsとその他のデスクトップos向けアプリも
同時に構築できます(ただし実行にはjavaインストールが必要)。

詳細は http://doc.tir.ne.jp/devel/clan のドキュメントを見てください。

最新安定版は `0.0.6 <https://github.com/ayamada/clan/tree/0.0.6>`_ です。

現在 `use_lein <https://github.com/ayamada/clan/tree/use_lein>`_
ブランチにて大幅改変中です。


ChangeLog
---------

-  `<ChangeLog>`_


Link
----

-  `Clojure <http://clojure.org/>`_

   -  `CheatSheet <http://clojure.org/cheatsheet>`_
   -  `ClojureDocs <http://clojuredocs.org/>`_
   -  `Clojure for Android <https://github.com/sattvik/clojure>`_

      -  `Deep Blue Lambda <http://www.deepbluelambda.org/>`_: Clojure for Android のメンテナの人のサイト。nekoのオリジナル版もこの人が作った。
      -  `Clojure REPL <https://play.google.com/store/apps/details?id=com.sattvik.clojure_repl>`_: この人が作った、android用clojureのREPLコンソール。一行コンソールからだけではなく、Nailgun内蔵で外からVimClojure経由の操作もできる。android実機の環境で「○○クラスは標準で入ってるのか？」みたいな調べ物をするのに便利。

-  `LibGDX <http://libgdx.badlogicgames.com/>`_

   -  `libgdx API <http://libgdx.badlogicgames.com/nightlies/docs/api/overview-summary.html>`_
   -  `Wiki <http://code.google.com/p/libgdx/wiki/TableOfContents>`_
   -  `Androidゲームプログラミング A to Z <http://www.impressjapan.jp/books/3113>`_\ (`amazon <http://www.amazon.co.jp/dp/4844331132>`_): `Beginning Android Games <http://www.apress.com/9781430230427>`_\ の日本語訳版。良書。ただし本家の方は\ `2nd Edition <http://www.apress.com/9781430246770>`_\ が出てるが日本語訳版は初版ベース。
   -  `libgdxメモ <http://doc.tir.ne.jp/devel/clan/libgdx>`_: ayamadaがメモしたlibgdxのノウハウ集。

-  `Android <http://developer.android.com/index.html>`_

   -  `ProGuard <http://proguard.sourceforge.net/>`_, `ProGuard(Android Developers) <http://developer.android.com/tools/help/proguard.html>`_\ (`日本語訳 <http://www.techdoctranslator.com/android/developing/tools/proguard>`_)

-  `Neko(forked by Alexander Yakushev) <https://github.com/alexander-yakushev/neko>`_

   -  `reference <http://alexander-yakushev.github.com/neko/>`_
   -  `Clojure Development on Android <http://clojure-android.blogspot.jp/>`_: nekoのメンテナの人の解説ブログ。

-  `launch4j <http://launch4j.sourceforge.net/>`_

-  `lein-droid <https://github.com/alexander-yakushev/lein-droid>`_: leinでandroidアプリを生成する為のleinプラグイン。開発中はapkにnREPLサーバ機能を自動で組み込んで、android実機の外からプロセスにREPL接続できるようにする機能あり。以前はwindowsでうまく動かなかったが対応されたようなのであとで組み込む。

-  `clojurelibgdx <https://github.com/thomas-villagers/clojurelibgdx>`_: 類似プロジェクト。


License
-------

CLAN have `Apache License 2.0 <http://www.apache.org/licenses/LICENSE-2.0>`_.

Which can be found in the file `LICENSE <LICENSE>`_.

and see http://doc.tir.ne.jp/devel/clan/license .


WANTED
------

Please give me(ayamada) a work.

ayamadaは現在お仕事募集中です。

- http://tir.jp/RSM


Apps built with CLAN
--------------------

I'm glad, if you made apps by CLAN, and inform that to
`@rnkv(=ayamada) <https://twitter.com/rnkv>`_.
I will append to this list.

(もしあなたがCLANを使って何かアプリを作ったら、
`@rnkv(=ayamada) <https://twitter.com/rnkv>`_
まで教えてもらえると嬉しいです。このリストに追加します。)

--------------

-  http://vnctst.tir.jp/ja/games/driftcat_underworld.html

   -  A short game that the cat want to return to home from
      empire of underworld.
      Wrote by ayamada.
      (sorry, this app is required to read japanese.)

   -  猫を操作して地底帝国からの脱出を目指す、ぬるいRPGです。
      ayamada作。

-  http://vnctst.tir.jp/ja/games/space_drop.html

   -  Packaged version of bundled sample app 'space drop'
      Wrote by ayamada.
      (sorry, this page written in japanese.)

   -  同梱のサンプルアプリ'space drop'のパッケージ版です。
      ayamada作。



