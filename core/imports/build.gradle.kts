plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    api(project(":core:finance"))
    implementation("org.apache.poi:poi:6.0.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.register<JavaExec>("checkLocalStatements") {
    description="Read-only local adapter check; prints format success only, never financial rows."
    classpath=sourceSets["test"].runtimeClasspath
    mainClass="com.wealthos.imports.LocalStatementCheck"
    args(providers.gradleProperty("wealthos.statementFiles").orNull?.split('|') ?: emptyList<String>())
}
