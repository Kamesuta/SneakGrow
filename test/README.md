# SneakGrow E2E テスト

Paper をバージョンごとにダウンロードしてスーパーフラットのサーバーを起動し、mineflayer が畑の横でしゃがみを繰り返した後に小麦が成長したことを確認するテスト。

同時に、子牛の年齢が`-100`から`0`になって成体になることと、繁殖クールダウン中の牛の年齢が`100`から`0`になり、再び繁殖可能になることも確認する。

```powershell
cd test
npm install
npm test
```

一部だけ実行する場合:

```powershell
npm test -- 1.20.1,26.2
```

### Javaバージョンについて

マイクラのバージョンによって必要なJavaバージョンが異なるため、テストを行う前にJavaを複数バージョンセットアップし、以下の環境変数をセットしてください。

```powershell
$env:E2E_JAVA_11 = 'C:\Program Files\Java\jdk-11\bin\java.exe'
$env:E2E_JAVA_16 = 'C:\Program Files\Java\jdk-16\bin\java.exe'
$env:E2E_JAVA_17 = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$env:E2E_JAVA_21 = 'C:\Program Files\Java\jdk-21\bin\java.exe'
$env:E2E_JAVA_25 = 'C:\Program Files\Java\jdk-25\bin\java.exe'
```

ダウンロードされたものは `test/.cache`、各サーバーのログやワールドデータは `test/runs` に残ります。
