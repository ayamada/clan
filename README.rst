.. image:: https://github.com/ayamada/clan/raw/master/doc/img/logo_b.png

CLAN : Clojure, LibGDX, Android, Neko development framework
===========================================================

CLAN supply to an environment that develop android application using
libgdx by only clojure code. You must understand to clojure and libgdx.

WARNING: This package version is 0.0.1-EXPERIMENTAL. It cannot upgrade
to future version 1.0.0 . Please try to CLAN if you still OK.

--------------

**CLAN : Clojure, LibGDX, Android, Neko 開発フレームワーク**

これは「clojureのコード書くだけでlibgdx使ったandroidアプリができた！」を
実現する為の開発フレームワークです。
別途、clojureとlibgdxの知識が必要です。

注意：現在のバージョンは 0.0.1-EXPERIMENTAL です。
TODOにある「lein化」と「platform別ライブラリ化」が完了した時点で
バージョン 1.0.0 とします。 この 0.0.1 から 1.0.0
へのアップデートは非常に困難ですが、
それでもいい人は試してみてください。

( cal 内は多分そのまま使えますが、 configure.in 等は移行不可能です。)

Advantages / Disadvantages
--------------------------

Advantages

-  LibGDX get the power of Lisp
-  Deploy to apk for Android, exe for Windows, jar for other desktop OS

   -  But ``*.exe`` need Java Runtime Environment
   -  If you dont have android-sdk, still you can build only jar

Disadvantages

-  Too fat (jar:8.3M over, exe:8.4M over, apk:2.5M over)
-  Boot slowly
-  frequent GC
-  You must write specialized clojure-code for Android (typical
   clojure-code work better on PC, but it work slowly on Android)

   -  But we have macro! Maybe CLAN will be provided many macros in
      future.

-  Deploy for GWT and iOS were not supported yet

Miscellaneous

-  using autoconf, make, maven (change to only lein in future)

--------------

長所

-  libgdxとlispが両方そなわり最強に見える
-  android向けapk、windows向けexe、その他デスクトップos向けjar、を生成

   -  ただし ``*.exe`` は実行にjavaインストールが必要
   -  android-sdkを入れずに ``*.jar`` だけ生成する事もできます

短所

-  でかい(jar:8.3M～, exe:8.4M～, apk:2.5M～)
-  起動が遅い
-  頻発するGC
-  android向けに特化したclojureコードを書かなくてはいけない
   (普通の書き方のclojureコードは、PCでは問題なく動くがandroid上で速度が出ない)

   -  だが我々にはマクロがある。時の経過と共にCLAN向けマクロは充実する予定

-  libgdxはGWTとiOSへのデプロイにも対応しているが、これらはCLANでは未対応
-  libgdxは日本語のドキュメントや解説がほとんどない

その他の特徴

-  autoconf + make + maven 使用(将来はleinのみにしたい)

Requirement
-----------

see http://doc.tir.ne.jp/devel/env .

(sorry, this document is written in japanese, please use translator.)

On 2013/02/28, CLAN is required to ...

- jre 1.6 or 1.7
- jdk 1.6 (not supported jdk 1.7)
- clojure 1.4.0 (not supported clojure 1.5.0)
- libgdx 0.9.7
- android-2.3 (API LEVEL 9) or greater

I look see that CLAN worked on microsoft windows vista(x86-32bit),
gentoo linux(x86-32bit) on 2013/02/28.

--------------

http://doc.tir.ne.jp/devel/env を見て、手元の環境を同様の状態に
構築しておいてください。

(これはwindows上に構築する前提の手順です。
windows以外なら手順が違いますが、もっと簡単に要求物を構築できるでしょう。)

CLANは、2013/02/28現在、以下の環境を要求します。

- jre 1.6 もしくは 1.7
- jdk 1.6 (jdk 1.7 は未サポート)
- clojure 1.4.0 (clojure 1.5.0 は未サポート)
- libgdx 0.9.7
- android-2.3 (API LEVEL 9) 以上

2013/02/28 に、windows vista(32bit)とgentoo(x86-32bit)上にて
動作する事を確認しました。

Usage
-----

sorry, currently only japanese.

(TODO: 英語版の用意)

プロジェクトの設置
~~~~~~~~~~~~~~~~~~

まず、以下のようにプロジェクト設置ディレクトリを作ります。

::

    mkdir -p package/projname
    cd package/projname
    git clone git://github.com/ayamada/clan.git
    sh clan/script/spread_current_dir.sh

-  ``package`` と ``projname`` は自分で好きなように決めてください。
-  最後の ``clan/script/spread_current_dir.sh`` によって、
   package/projname 内に、各サンプルファイルが設置されます。

これは以下の構造を想定しています。

-  package/projname/clan/ として、CLAN本体を設置。
-  package/projname/.gitignore に、 CLAN本体を除外する設定を記述。

   -  この .gitignore も spread\_current\_dir.sh によって生成されます。

-  package/projname/ に、作りたいアプリ本体の各ファイルを設置。

   -  これらのファイルも spread\_current\_dir.sh
      によって大体生成されます。

-  package/ に、 ``package/projname/``
   で生成されたリリース版実行バイナリを
   配布する為のパッケージング/デプロイする為の Makefile か何かを設置。

   -  これについてはCLANは何も提供しません。自分で用意してください。

-  package/.git に、自プロジェクトを保存。

   -  これについてもCLANは何も提供しません。自分で行ってください。
   -  前述の通り、 package/projname/.gitignore によって、
      CLAN本体は除外されます。

サンプルの構築
~~~~~~~~~~~~~~

以下のコマンドで、サンプルアプリを構築して起動してみます。
動く事を確認してください。

::

    autoconf && ./configure
    make jar-exec

このサンプルアプリの名前はdropです
( http://code.google.com/p/libgdx/wiki/SimpleApp をベースにしています)。

起動ロゴが出て消えるまでがclanが管理する部分です。
ロゴ等はあとで好きに入れ替えましょう。
それ以降は完全にサンプルアプリ側に制御が渡されます。

更に以下のコマンドで、usbデバッグ接続しているandroid端末に
サンプルアプリをインストールします。

::

    make apk-install

端末のアプリ一覧から起動し、動く事を確認してください。

動く事が確認できたら、一旦中間ファイルを削除します。

::

    make distclean

開発の開始
~~~~~~~~~~

上記ディレクトリをベースに、以下のフローで開発を行います。

1. configure.ac を適切に書き換える

2. 必要に応じて、layout/\*.in android/AndroidManifest.xml.in 等を編集

   -  ``*.in`` というのは、autoconfによる書き換えを行う元ファイル。
      とりあえず、以下だけ把握していればok。

      -  後述の ``autoconf && ./configure`` 実行時に書き換えも実行される
      -  例えば ``hoge.xml.in`` から ``hoge.xml``
         が、同じディレクトリに生成される
      -  ファイル内の ``@HOGE@`` のような部分のみが書き換えられる
      -  後述の ``make clean``
         系を実行すると、生成されたファイルも消える

   -  この辺りのファイルの実際の役目についてはlibgdxおよびandroidの
      ドキュメントをぐぐってください。

3. cal/src/main/clojure/{package}/.../al.clj 等を作成/編集

   -  これがアプリの本体です。

4. 利用するアイコン、リソース等を assets/ res/ l4j/ 等に追加/変更

5. ``autoconf && ./configure`` する

   -  これにより、環境のチェックと ``*.in`` ファイルからの
      書き換え(ファイル生成)が実行される。
   -  知っている人は ./configure にパラメータや引数を渡したくなるかも
      しれませんが、ここでは一般的な autoconf の利用法から外れた
      使い方をしている為、何も渡さないようお願いします。

6. ``make jar-exec`` する

   -  ソースにエラー等がなければ、\ ``*.jar``\ ファイルが生成され、実行されます。
   -  エラーが出た場合は 2. か 3. に戻って修正してください。
   -  その他のmakeで指定できるターゲットは後述。

7. 動作したが気に食わない/更にコードを追加する

   -  ``make clean`` してから 3. に戻ってください。

8. ``make apk-install`` して実機で動作確認

   -  このコマンドでデバッグ版のandroid用\ ``*.apk``\ が生成され、
      接続されている端末にインストールされます。
      端末のアプリ一覧から入ったアプリを選択して動かしてみてください。
   -  この場合も修正し直す場合は ``make clean`` してから 3.
      に戻ってください。

これらのコンパイルは結構時間がかかる為、
デバッグ中は、直に\ ``*.clj``\ ファイルを読み込むようにするといいでしょう。
これならアプリを再起動するだけですぐに反映されます。

(サンプルアプリにもこの機能を入れてあります。
``load-*``\ 系関数を使って外部にある、 ``(ns jp.ne.tir.drop.drop ...)``
ではじまるスクリプトを読み込む事で、既にコンパイルされている
jp.ne.tir.drop.drop モジュールの各関数をメモリ上でのみ上書き
したような状態にする事ができます。)

-  ただしPCではすぐにロードされますが、
   android実機でのスクリプトのロードはPCとは違い、
   非常に時間がかかります。注意！ (サンプルアプリ程度の量で1分ぐらい)

   -  また、androidで動的に変更した内容は、
      次回のアプリ起動時にも引き継がれてしまうようです。
      REPLを利用したデバッグ時には注意が必要かもしれません。

「これで完成だ」と思ったら、リリースを行います。

リリース作業
~~~~~~~~~~~~

デスクトップ向けos汎用の\ ``*.jar``\ 、windows向けの\ ``*.exe``\ 、
android向けの署名付き\ ``*.apk``\ を生成します。

-  リリースするので、 configure.ac を編集し、
   バージョン番号等をリリース向けに変更します。
   (具体的には「-SNAPSHOT」を取る、とかそういう作業)

   もし AndroidManifest.xml.in にてデバッグの為だけに INTERNET や
   WRITE\_EXTERNAL\_STORAGE を有効にしていた場合は、
   必要に応じてコメントアウトしておきます。
   (これは将来には何らかの手段で、手でいじらなくてすむようにする予定…)

   またバージョンアップの場合は、忘れずに
   ``PROJECT_ANDROID_VERSIONCODE`` の値を上げます。

   ソースをgit等に保存している場合は、リリースタグ等も作っておきます。

-  まだapk署名用の鍵を作ってなければ、作成します。

   -  コンソールから以下のコマンドを実行します。
      質問されるので適切に入力し、パスワード等も決めてください。

      keytool -genkey -keystore path/to/NAME.keystore -validity 36500
      -alias NAME

   -  ファイル名およびalias名は自分で決めてください。
      この辺りの詳細についてはぐぐってください。
   -  このファイルをなくすとgoogle playでのバージョンアップが
      できなくなるので、バックアップを取っておいた方がいいでしょう。

-  上の署名用鍵の情報を、ローカルのmavenのsettings.xmlに設定する

   -  以下の内容のsettings.xmlを、 ``~/.m2/settings.xml`` に設置します。
      既に設置されている場合はいい感じに混ぜてください。
      この辺りの詳細も必要であればぐぐってください。

      ::

          <settings>
            <profiles>
              <profile>
                <id>clan-sign</id>
                <properties>
                  <sign.keystore>d:/path/to/hoge.keystore</sign.keystore>
                  <sign.alias>hoge</sign.alias>
                  <sign.storepass>xxxxxxxx</sign.storepass>
                  <sign.keypass>xxxxxxxx</sign.keypass>
                </properties>
              </profile>
            </profiles>
            <activeProfiles>
              <activeProfile>clan-sign</activeProfile>
            </activeProfiles>
          </settings>

   -  上記の ``sign.keystore sign.alias sign.storepass sign.keypass`` を
      自分の生成した鍵にあうように変更しておいてください。

      -  msysではドライブ指定に注意が必要です。
         上記のような感じなら大丈夫でしょう。

-  例によって ``autoconf && ./configure`` した後、 ``make release``
   を実行します。 エラーにならずに最後まで完了すれば、 ``target/``
   の中に以下の3ファイルが生成されます。

   -  ``appname-android.apk  appname-desktop.exe  appname-desktop.jar``

-  必要に応じて、これらのファイルを配布物としてパッケージングしたり、
   google playに登録したりします。

-  リリースしたので、 configure.ac を編集し、
   バージョン番号等を非リリース向けに変更します。
   (「-SNAPSHOT」をつけなおす、とかそういう作業)

   AndroidManifest.xml.in も、元に戻します。

以上。

その他
------

用語について
~~~~~~~~~~~~

-  clan : このフレームワーク、配布物一式、ディレクトリ名
-  cal : CLAN ApplicationListener。アプリ本体、これを主にいじる。 cal/
   が実体
-  cbl : CLAN BootLoader。ブート画面部分。clan/cbl/ 内にソースあり

ディレクトリ解説
~~~~~~~~~~~~~~~~

いじるべきソースが入っているもの

-  cal/

   -  アプリ本体のソース置き場。この中にApplicationListenerを書く
   -  外部ライブラリを利用したい時は、この中のpom.xml.inに追加する。
      その際にはscopeをcompileにする事。providedだとjarに含まれない。

-  layout/

   -  androidアプリとデスクトップ向けjarの起動部分のコード置き場。
      コンパイル時にはこれらは android/ と desktop/ の中にコピーされる。

リソース、設定類

-  assets/

   -  apk, jar, exe
      の全てのバイナリ内に埋め込まれるリソースファイル群置き場

-  assets/cbl/

   -  clanのブート画面用のリソース。差し替え可能

-  assets/drop/

   -  サンプルアプリで使用しているリソース。一から作る時は丸ごと捨ててよい

-  assets/icon/

   -  desktop版のプロセスアイコン。差し替え可能

-  android/

   -  この中でapkを生成します。
   -  この中の AndroidManifest.xml.in はいじる必要あり

-  res/

   -  android向けリソース置き場。

-  l4j/

   -  launch4j用のリソース。exe向け設定とアイコン。

基本的にはいじる必要のないもの

-  target/

   -  make release時に自動生成されます。中にリリース版のapk, exe,
      jarができる

-  clan/

   -  clanの配布物一式

-  desktop/

   -  この中でjarを生成します。

-  tmp/

   -  mke dep時に自動生成されます。主にファイル展開に使う

CLAN自体の情報を取得する
~~~~~~~~~~~~~~~~~~~~~~~~

-  clan/info/ によって、 jp.ne.tir.clan.Info が提供されます。
-  詳細については、
   ``clan/info/src/main/java/jp/ne/tir/clan/Info.java.in``
   を確認してください。しかし実際に使う可能性があるのは ``Info/debug``,
   ``Info/buildNumber``, ``Info/BuildDate`` ぐらいでしょう。
   それぞれ、デバッグフラグ、ビルド番号(単なるepoch)、ビルド日時です。

makeターゲット一覧
~~~~~~~~~~~~~~~~~~

makeの依存関係はドットファイルのフラグファイルで管理しています。
これは主に、mavenのローカルリポジトリに入るファイルを判定する為です。

実際の依存関係のグラフは、同梱の ``doc/dependencies.*`` を
参照してみてください。

-  make info

   -  clan/info/ にある、clan自体の情報を保持するパッケージを構築し、
      mavenのローカルリポジトリに登録します。
      これはビルド情報を含める為、結構頻繁に更新されます。

-  make dep-libgdx

   -  libgdxを公式サイトからダウンロードし、
      mavenのローカルリポジトリに登録します。

-  make dep-neko

   -  nekoをclojars.orgからダウンロードし、一部をコンパイルし、
      mavenのローカルリポジトリに登録します。

-  make dep

   -  dep-libgdx と dep-neko の両方を実行します。

-  make layout

   -  layout/ 配下にあるソースファイルを適切な位置に配置します。
      これはandroidにて、パッケージ名によってメインアクティビティの
      定義位置が変わってしまう対策です。

      -  もっといい方法はありそうだけど調査は後回し

-  make cal

   -  cal/ 配下にある、clojureで書いたアプリ本体を構築し、
      mavenのローカルリポジトリに登録します。

      -  calとは「clojure ApplicationListener」の略です。
         そして「ApplicationListener」は、libgdxのApplicationListenerです。

-  make cbl

   -  clan/cbl 配下にある、ブートローダ本体を構築し、
      mavenのローカルリポジトリに登録します。

      -  cblとは「clojure BootLoader」の略です。

-  make jar

   -  desktop/ 配下に、デスクトップ向けの\ ``*.jar``\ を生成します。

-  make jar-exec

   -  上記jarを生成し、実行します。

-  make apk

   -  android/
      配下に、デバッグ署名のandroid向けの\ ``*.apk``\ を生成します。

-  make apk-install

   -  上記apkを生成し、接続している端末にインストールします。

-  make release-jar

   -  target/ 配下に、リリース版のjarをクリーンに生成します。

-  make release-exe

   -  target/ 配下に、リリース版のexeをクリーンに生成します。

-  make release-apk

   -  target/
      配下に、リリース版の正式な署名のapkをクリーンに生成します。

-  make release

   -  target/ 配下に、上記3ファイルを全て生成します。

-  make clean

   -  全てのクラスファイル、実行ファイル、リリースファイルを削除します。

-  make ac-clean

   -  ``autoconf && ./configure`` が生成するファイルを削除します。

-  make distclean

   -  ``make clean ac-clean`` と同じです。

-  make maintainer-clean

   -  distcleanに加え、ダウンロードしたlibgdx配布物も削除します。

-  make depclean info-clean layout-clean cal-clean cbl-clean jar-clean
   apk-clean

   -  それぞれのターゲットのみ削除します。

CLAN自身の開発手順
~~~~~~~~~~~~~~~~~~

自分用。

CLANはサンプルアプリとセットで開発を行う。

::

    mkdir -p clan_parent
    cd clan_parent
    git clone git@github.com:ayamada/clan.git
    sh clan/script/spread_current_dir.sh
    rm -rf clan/sample && mkdir -p clan/sample

-  通常アプリとは違い、アプリのリリースはしないのでディレクトリは一段でよい
-  push可能なように、sshでgit cloneする
-  サンプルを展開する
-  うっかり再展開してしまわないように、一時的に clan/sample/
   を空にしておく

この状態で開発を行う。

まず最初に al.clj をいじって、
外部ファイルの動的ロード(後述)とプロファイリングができるようにしておく事。

サンプルアプリは今のところ、非リリース版では以下の機能が有効になる。
(将来には変更になる可能性大)

-  起動時に、外部ファイル/URLからclojureファイルを読み込む。
   この機能は、アプリの再ビルドなしに jp.ne.tir.drop.drop モジュールを
   更新するのに使える(上記ファイルとしてcal内のdrop.cljをそのまま指定する)

   -  android端末でこの機能を使って外部URLからファイル読み込む場合、
      AndroidManifest.xmlにINTERNETが必要。
      またファイルのロード(というかdexコンパイル)にはかなりの時間がかかる。

-  起動後はEキーを押すと、上記とは別の外部ファイル/URLのファイル内容をevalする
   この機能はREPLの粗悪品として使える。

   -  本当はnREPLを使いたいが、デバッグ版とリリース版の切り分けがうまくいかず
      とりあえず簡易版としてこれを実装。
   -  たまにandroid内でスレッドプールからスレッドが確保できなくなる時がある。
      原因不明。あとで調べなくてはならない。


一通り開発ができたら以下を行う。

-  上記で変更した al.clj を元の状態に戻しておく
-  もし必要であれば、サンプルアプリのリリース版を生成し、
   google playに新しいapkを登録したり、jar/exeの配布物を生成する。
   -  ここの手順は上記の「リリース作業」を確認する事。

その後、以下を実行してsampleに反映し直す。

::

    make maintainer-clean
    cp -a Makefile.in android assets cal configure.ac desktop l4j layout res clan/sample

要は、 clan/ 以外の全ファイルを clan/sample/ へと戻している。

その後、忘れずにgitに保存する。 この際には必ず、
**変更ファイル一覧および差分を確認** し、うっかりして
**前述のpath部分の変更がコミットされたりする事がないよう注意** する。

-  サンプルアプリ部分をコミットしたくなったら、前述の cp -a
   を行ってからコミットする事。

CLAN自身のリリース手順
~~~~~~~~~~~~~~~~~~~~~~

上記の通りに開発を行い、gitに保存したところまで進めておく事。

0. ChangeLog にリリースの記録

   -  gitのコミットログを確認し、重要な変更点があるならきちんと記入する事

1. script/settings.sh のバージョン番号から、 ``-SNAPSHOT`` を除去
2. git add ChangeLog script/settings.sh
3. git commit -m 'version X.Y.Z releasing'
4. git tag -a タグ名 -m 'メッセージ'
5. script/settings.sh のバージョン番号を上げ、 ``-SNAPSHOT`` を付与
6. git add script/settings.sh
7. git commit -m 'version X.Y.Z released'
8. git push
9. git push origin --tags

時間があればもう少し今風に改善したいところだが…

FAQ
---

My app is too slow on android-real-machine. / android実機で超遅い
   enabling
   `*warn-on-reflection* <http://clojure.org/java_interop#Java%20Interop-Type%20Hints>`_,
   and insert type specifier. it was used by
   `clojure-maven-plugin <https://github.com/talios/clojure-maven-plugin#configuring-your-clojure-session>`_.

   `*warn-on-reflection* <http://clojure.org/java_interop#Java%20Interop-Type%20Hints>`_
   を有効にして、型指定しまくる。
   `clojure-maven-plugin <https://github.com/talios/clojure-maven-plugin#configuring-your-clojure-session>`_\ からも使える。


Why cannot I compile ``*.clj``, it was skipped. / なぜか ``*.clj`` がスキップされてコンパイルされない
   set to encoding = utf-8. This is spec of clojure-maven-plugin
   probably.

   文字コードをutf-8にしてみる。clojure-maven-pluginの仕様のようです。


How to upgrade CLAN / CLANバージョンアップのやりかた
   replace ``clan/`` directory, or ``git pull`` on ``clan/``
   directory. but, you must check to ChangeLog for incompatible
   changes at before.

   ``clan/`` ディレクトリを丸ごと新しいものに交換する。 もしくは
   ``clan/`` ディレクトリ内で ``git pull`` を実行。
   だが先にChangeLogを見て、非互換な変更がないか確認する事。


Where is save data for desktop jar/exe / デスクトップ向けjar/exeのセーブデータの場所
   it saves ``C:\Users\{USERNAME}\.pref\`` (for windows), or ~/.pref/
   (other unix like OS). point is .prefs in home-directory by OS.

   windowsなら、 ``C:\ユーザー\{ユーザ名}\.pref\`` 、
   windows以外なら、 ~/.pref/ 。
   要はosの認識するホームディレクトリにある .prefs 。


I want to change/erase background console output. / 背景のコンソール出力を変更したい/表示させたくない
   You edit ``clan/cbl/src/main/java/jp/ne/tir/clan/BootLoader.java``

   ``clan/cbl/src/main/java/jp/ne/tir/clan/BootLoader.java`` をいじる


I want to change color in boot screen. / ブート画面の色を変更したい
   You edit ``clan/cbl/src/main/java/jp/ne/tir/clan/BootLoader.java``

   ``clan/cbl/src/main/java/jp/ne/tir/clan/BootLoader.java`` をいじる


I want to change boot screen more better. / その他ブート画面をもっとよくしたい
   You edit ``clan/cbl/src/main/java/jp/ne/tir/clan/BootLoader.java``

   ``clan/cbl/src/main/java/jp/ne/tir/clan/BootLoader.java`` をいじる


What is something wrong to collision-detection of sample-app? / サンプルアプリの当たり判定おかしくない？
   it can catch items by mouth only.

   口の部分にのみ当たり判定があります。


I cannot press 'E' key on real-android-machine. / android実機でEキーなんて押せねーよ！
   you can edit code yourself, or use bluetooth keyboard.

   自分でコードをいじれ。もしくはbluetoothキーボードを用意。


What is CLAN logo? / CLANのロゴは何？
   This is my family emblem. Change more better logo on later.
   (because it is too cutting corners.) logo's emblem part came from
   eps-file that distributed by
   http://eps.crest-japan.net/index\_en.php .

   うちの家の家紋です。
   あとでもっとちゃんとしたロゴを作る(あまりにも手抜きなので)。
   家紋部分は http://eps.crest-japan.net/ からepsファイルを
   貰ってきて加工して作った。


What is assets of sample game? / サンプルゲームの画像や音は何？
   all assets were made by me. license is EPL-1.0, similar clan.

   自作した。全部俺。ライセンスはclanと同様、EPL-1.0とする。

お願い
------

開発者であるayamadaは資金が残りわずかであり、
あんまりCLANのメンテをやってる時間がありません。
おかしい部分や足りてない機能はpull-reqしてもらえると助かります。

(TODO: ここにpaypalの「donate」ボタンを設置)

Link
----

-  `Clojure <http://clojure.org/>`_

   -  `CheatSheet <http://clojure.org/cheatsheet>`_
   -  `ClojureDocs <http://clojuredocs.org/>`_
   -  `Clojure for Android <https://github.com/sattvik/clojure>`_

-  `LibGDX <http://libgdx.badlogicgames.com/>`_

   -  `libgdx
      API <http://libgdx.badlogicgames.com/nightlies/docs/api/overview-summary.html>`_
   -  `Wiki <http://code.google.com/p/libgdx/wiki/TableOfContents>`_
   -  `Androidゲームプログラミング A to
      Z <http://www.impressjapan.jp/books/3113>`_\ (`amazon <http://www.amazon.co.jp/dp/4844331132>`_):
      `Beginning Android
      Games <http://www.apress.com/9781430230427>`_\ の日本語訳版。良書。ただし本家の方は\ `2nd
      Edition <http://www.apress.com/9781430246770>`_\ が出てるが日本語訳版は初版ベース。

-  `Android <http://developer.android.com/index.html>`_

   -  `ProGuard <http://proguard.sourceforge.net/>`_, `ProGuard(Android
      Developers) <http://developer.android.com/tools/help/proguard.html>`_\ (`日本語訳 <http://www.techdoctranslator.com/android/developing/tools/proguard>`_)

-  `Neko(forked by Alexander
   Yakushev) <https://github.com/alexander-yakushev/neko>`_

-  `launch4j <http://launch4j.sourceforge.net/>`_

-  `CLANによるandroidアプリ開発 <http://doc.tir.ne.jp/devel/clan>`_:
   俺が書いてる。まだ途中。

License
-------

Copyright (c) atsuo yamada.

Distributed under the Eclipse Public License, the same as Clojure.

Which can be found in the file `epl-v10.html <epl-v10.html>`_.

TODO
----

-  important

   -  http://ch.nicovideo.jp/indies-game
      にプラットフォーム部門で応募して五万円を狙う為の動画を作成
   -  「autoconf+make+maven」はやめて、leinで統一する(しかし先にleinの使い方を学ぶ必要あり…)
   -  cal内にて、「android限定のコード(と利用ライブラリ)」と
      「desktop限定のコード(と利用ライブラリ)」をうまく切り分けられるようにする

      -  現状ではlayout内のjavaコードでのみの切り分けなので不便

-  later

   -  開発版ビルドとリリース版ビルドの切り分け部分の改善

      -  「開発版でのみjarに含めるライブラリ」みたいな事ができるようにしたい
      -  リリース版と開発版でAndroidManifest.xmlのuses-permissionの変更
         (要は開発時のみINTERNETとWRITE\_EXTERNAL\_STORAGEを有効にしたい的な)

   -  collecting and documentation to know-how in clojure(for android),
      libgdx, android, neko
   -  refactoring sample app

      -  clean-up code
      -  implement to timeout on eval-file
      -  boot nREPL-server at debug-time
      -  FPSが低下した時にEキーを取りこぼす時がある。Gdx.input.justTouched()と同じような処理が必要(おそらくキーリスナを作らないと駄目)

   -  増やし忘れ対策 of PROJECT\_ANDROID\_VERSIONCODE in configure.in
   -  improve BootLoader

      -  LwjglApplicationやAndroidApplicationから、ApplicationListenerを完全に差し替えてしまう事が可能か調べる(勿論portableな方法でないといけない)

-  more later

   -  enable proguard.cfg ( remove ``-dontshrink`` and tune-up )
   -  change CLAN logo to more better
   -  maintain documents
   -  translate from japanese comment to english in source
   -  report to libgdx community
   -  how to use ``libgdx/extensions/*``
   -  more modular
   -  more portable
   -  more simple


