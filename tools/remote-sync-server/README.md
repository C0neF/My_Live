# Remote sync server

Local development server for the My Live remote sync room protocol.

```powershell
cd tools/remote-sync-server
npm install
npm start
```

For Android devices connected with ADB, expose the local server to the app:

```powershell
adb reverse tcp:51999 tcp:51999
```

The app debug build automatically prefers `ws://127.0.0.1:51999/sync` when it is reachable and the sync server URL setting is blank.
