# YouTubeCounter
This is a simple application with purpose of displaying likes and views of YouTube stream at realtime.

## Structure
1. `composeApp` - a desktop app itself (currently supports only Windows msi image).
2. `server` - a server which providing exchanging of google auth code to access token.

## Building
1. Clone project.
2. Ensure you have installed `Java 21`.
3. To build application run `./gradlew :composeApp:packageMsi` to build dev .msi package or `./gradlew :composeApp:packageReleaseMsi` for release .msi package.
4. To build server run `./gradlew :server:buildFatJar` or `./gradlew :server:buildImage` if you want to build docker image.

## Licensing
The project is licensed under [MIT license](LICENSE)

## Contributing
You are free to create Pull Requests or issues with feature request. There are no clear instructions for this.
