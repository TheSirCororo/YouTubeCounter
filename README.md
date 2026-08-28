# YouTubeCounter
This is a simple application with purpose of displaying likes and views of YouTube stream at realtime.

## Structure
1. `composeApp` - a desktop app itself (currently supports only Windows msi image).
2. `server` - a server which providing exchanging of google auth code to access token.

## Downloads
Every push to `master` builds the desktop app for Windows, macOS and Linux; the packages are attached to that run in the Actions tab. Tagged releases (`v1.0.5`) additionally publish them to [Releases](https://github.com/TheSirCororo/YouTubeCounter/releases):

| Platform | Package |
| --- | --- |
| Windows | `.msi` |
| macOS | `.dmg` (Intel and Apple Silicon, unsigned - allow it in System Settings on first launch) |
| Linux | `.deb`, `.rpm`, and a portable `.tar.gz` |

## Building
1. Clone project.
2. Ensure you have installed `Java 21`.
3. To build the app for the platform you are on, run `./gradlew :composeApp:packageReleaseDistributionForCurrentOS` (drop `Release` for a faster unminified build). Individual formats: `packageReleaseMsi`, `packageReleaseDmg`, `packageReleaseDeb`, `packageReleaseRpm`, `packageReleaseAppImage`. Each format can only be built on its own OS.
4. Pass `-PappVersion=1.0.5` to override the packaged version.
5. To build server run `./gradlew :server:buildFatJar` or `./gradlew :server:buildImage` if you want to build docker image.

## Deployment
The backend is deployed automatically: pushing to `master` builds the image, publishes it to GitHub Container Registry and rolls it out to the server, rolling back on its own if the new build does not come up healthy. Setup and host configuration are documented in [deploy/README.md](deploy/README.md).

## Licensing
The project is licensed under [MIT license](LICENSE)

## Contributing
You are free to create Pull Requests or issues with feature request. There are no clear instructions for this.
