# Kover Badge Plugin

![Pre Merge Checks](https://github.com/flaxoos/flax-gradle-plugins/actions/workflows/pre-merge.yaml/badge.svg)
![Language](https://img.shields.io/github/languages/top/flaxoos/flax-gradle-plugins?color=blue&logo=kotlin)
<a>![koverage](https://img.shields.io/badge/49.20-green?logo=kotlin&label=koverage&style=plastic&link=file:/Users/ido/IdeaProjects/kotlin-gradle-plugin-template/test-project/build/reports/kover/html/)</a>

The KoverBadgePlugin is a Gradle plugin designed to embed a badge in the project's readme, showcasing test coverage from
the Kover Gradle Plugin. This badge is generated using the https://shields.io/badges service.

## How to use 👣
- Apply the kover plugin along with this plugin to your project:

```kotlin
plugins {
    id("org.jetbrains.kotlinx.kover") version [kover_version]
    id("io.github.flaxoos.kover-badge") version [kover_badge_version]
}
```
- Create a readme file

- Add this where you want the badge to appear `<a>![your-badge-name]()</a>`

- Configure the extension:

```kotlin
koverBadge {
    readme.set(file("path/to/your/README.md"))
    badgeName.set("your-badge-name")
    badgeStyle.set(Style.Plastic)
    spectrum.set(
        listOf(
            Color.blue from 0.0f,
            Color.cyan from 50.0f,
            Color.green from 90.0f
        )
    )
}
```

Execute the task:
```shell
./gradlew addKoverBadge
```

Then you'll see something like this:

<a>![your-badge-label](https://img.shields.io/badge/95.0-green?logo=kotlin&label=your-badge-label&style=plastic&link=file:/Users/ido/IdeaProjects/kotlin-gradle-plugin-template/test-project/build/reports/kover/html/)</a>

Clicking on it will forward to your kover html report, if one exists
### Configuration Options (see kdocs for further information):

- `readme`: The project readme file where the badge will be placed.
- `badgeLabel`: The label for the badge.
- `badgeStyle`: The style of the badge.
- `spectrum`: The color spectrum and coverage threshold for each color.
- `gitAction`: What git action should the task perform with the changes to the readme file.
- `ciDetection`: Mechanism for determining if the task is being executed within a CI/CD environment, so no git action would take place in CI

For more information about the shields.io specific parameters, visit https://shields.io/badges/static-badge
## Contributing 🤝

Feel free to open a issue or submit a pull request for any bugs/improvements.

## License 📄

This template is licensed under the MIT License - see the [License](License) file for details.
Please note that the generated template is offering to start with a MIT license but you can change it to whatever you wish, as long as you attribute under the MIT terms that you're using the template.
