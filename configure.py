import os, shutil

if not os.getcwd().split("/")[-1] == "tilleys-template-plugin": exit("bad directory")

shutil.rmtree(".git")

plugin_name = input("Plugin name: ")
description = input("Description: ")
package_name = input("Package name: ")

module = input("Module: ").capitalize()
hud_element = input("HUD element: ").capitalize()
command = input("Command: ").capitalize()

def replace_in_file(path, old, new):
    with open(path, "r", encoding="utf-8") as file:
        new_content = file.read().replace(old, new)
    with open(path, "w", encoding="utf-8") as file:
        file.write(new_content)


plugin_json_path = os.path.join(os.getcwd(), "src", "main", "resources", "rusherhack-plugin.json")
replace_in_file(plugin_json_path, "URL_HERE", plugin_name.replace(" ", "-"))
replace_in_file(plugin_json_path, "PACKAGE_HERE", package_name)
replace_in_file(plugin_json_path, "DESCRIPTION_HERE", description)

gradle_properties_path = os.path.join(os.getcwd(), "gradle.properties")
replace_in_file(gradle_properties_path, "NAME_HERE", plugin_name.replace(" ", "-"))

readme_path = os.path.join(os.getcwd(), "README.md")
replace_in_file(readme_path, "NAME_HERE", plugin_name.replace("-", " "))
replace_in_file(readme_path, "DESCRIPTION_HERE", description)

os.rename(
    os.path.join(os.getcwd(), "src", "main", "java", "lol", "tilley", "PACKAGE_HERE"),
    os.path.join(os.getcwd(), "src", "main", "java", "lol", "tilley", package_name.replace(" ", "-"))
)

main_path = os.path.join(os.getcwd(), "src", "main", "java", "lol", "tilley", package_name.replace(" ", "-"), "PluginMain.java")
replace_in_file(main_path, "PACKAGE_HERE", package_name)
replace_in_file(main_path, "NAME_HERE", plugin_name)

os.rename(os.getcwd(), os.path.join(os.path.dirname(os.getcwd()), plugin_name.replace(" ", "-")))

package_directory = os.path.join(
    os.getcwd(), "src", "main", "java", "lol", "tilley", package_name.replace(" ", "-")
)

plugin_main_path = os.path.join(package_directory, "PluginMain.java")
template_module_path = os.path.join(package_directory, "TemplateModule.java")

def handle_feature(feature_name, template_class_name, registration_line, suffix):
    template_path = os.path.join(package_directory, f"{template_class_name}.java")
    if feature_name:
        class_name = f"{feature_name}{suffix}"
        class_path = os.path.join(package_directory, f"{class_name}.java")
        os.rename(template_path, class_path)
        replace_in_file(class_path, template_class_name, class_name)
        replace_in_file(class_path, "PACKAGE_HERE", package_name)
        replace_in_file(plugin_main_path, template_class_name, class_name)
    else:
        os.remove(template_path)
        replace_in_file(plugin_main_path, registration_line, "")

handle_feature(
    module,
    "TemplateModule",
    "RusherHackAPI.getModuleManager().registerFeature(new TemplateModule());\n",
    "Module"
)

handle_feature(
    hud_element,
    "TemplateHudElement",
    "RusherHackAPI.getHudManager().registerFeature(new TemplateHudElement());\n",
    "HudElement"
)

handle_feature(
    command,
    "TemplateCommand",
    "RusherHackAPI.getCommandManager().registerFeature(new TemplateCommand());\n",
    "Command"
)

os.system(f"cd ..&&cd {plugin_name.replace(" ", "-")}")
