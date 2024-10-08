package confusedalex.thegoldeconomy;

import de.leonhard.storage.Yaml;
import org.apache.commons.lang.LocaleUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redempt.redlib.commandmanager.ArgType;
import redempt.redlib.commandmanager.CommandParser;
import redempt.redlib.commandmanager.Messages;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public final class TheGoldEconomy extends JavaPlugin {

  EconomyImplementer eco;
  private VaultHook vaultHook;
  Util util;
  Yaml configFile;

  @Override
  public void onEnable() {
    // Config
    configFile = new Yaml("config.yaml", getDataFolder().toString(), getResource("config.yaml"));

    if (!configFile.getString("base").equals("nuggets") && !configFile.getString("base").equals("ingots")) {
      getLogger().severe(
              "The base is not correctly defined in the config file! Only the values 'nuggets' and 'ingots' are allowed! \n"
                      + "If you don't have the base option in your config file, add: base: \"nuggets\" or \"ingots\"");
      getServer().shutdown();
    }

    // Language
    ResourceBundle bundle;
    String language = configFile.getString("language");
    HashMap<String, Locale> localeMap = new HashMap<>();
    localeMap.put("de_DE", Locale.GERMANY);
    localeMap.put("en_US", Locale.US);
    localeMap.put("zh_CN", Locale.SIMPLIFIED_CHINESE);
    localeMap.put("es_ES", LocaleUtils.toLocale("es_ES"));
    localeMap.put("tr_TR", LocaleUtils.toLocale("tr_TR"));
    localeMap.put("pt_BR", LocaleUtils.toLocale("pt_BR"));
    localeMap.put("nb_NO", LocaleUtils.toLocale("nb_NO"));
    localeMap.put("nl_NL", LocaleUtils.toLocale("nl_NL"));

    if (localeMap.containsKey(language)) {
      bundle = ResourceBundle.getBundle("messages", localeMap.get(language));
    } else {
      bundle = ResourceBundle.getBundle("messages", Locale.US);
      language = "en_US";
      getLogger().warning("Invalid language in config. Defaulting to English.");
    }

    // bStats
    int pluginId = 15402;
    new Metrics(this, pluginId);

    // Vault shit
    util = new Util(this);
    eco = new EconomyImplementer(this, bundle, util);
    vaultHook = new VaultHook(this, eco);
    vaultHook.hook();

    // Commands from RedLib
    ArgType<OfflinePlayer> offlinePlayer = new ArgType<>("offlinePlayer", Bukkit::getOfflinePlayer)
            .tabStream(c -> Bukkit.getOnlinePlayers().stream().map(Player::getName));
    new CommandParser(this.getResource("commands.rdcml"),
        Messages.load(this,
                this.getResource("messages.txt"),
                "messages_" + language + ".txt",
                Pattern.compile("help.bank%\\w+=\\w+%")))
        .setArgTypes(offlinePlayer)
        .parse()
        .register("TheGoldEconomy",
                new Commands(bundle, eco, configFile, util));

    // Event class registering
    Bukkit.getPluginManager().registerEvents(new Events(this, eco.bank), this);
    // If removeGoldDrop is true, register Listener
    if (configFile.getBoolean("removeGoldDrop"))
      Bukkit.getPluginManager().registerEvents(new RemoveGoldDrops(), this);

    // Update Checker
    if (configFile.getBoolean("updateCheck")) {
      new UpdateChecker(this, 102242).getVersion(version -> {
        if (!this.getDescription().getVersion().equals(version)) {
          getLogger().info(bundle.getString("warning.update"));
        }
      });
    }

    if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      new Placeholders(this).register();
    }
  }

  @Override
  public void onDisable() {
    // Save player HashMap to File
    for (Map.Entry<String, Integer> entry : eco.bank.getPlayerBank().entrySet()) {
      String key = entry.getKey();
      int value = entry.getValue();

      eco.bank.getBalanceFile().getFileData().insert(key, value);
    }
    eco.bank.getBalanceFile().write();

    // Save FakeAccount HashMap to File
    for (Map.Entry<String, Integer> entry : eco.bank.getFakeAccounts().entrySet()) {
      String key = entry.getKey();
      int value = entry.getValue();

      eco.bank.fakeAccountsFile.getFileData().insert(key, value);
    }
    eco.bank.fakeAccountsFile.write();

    vaultHook.unhook();

    getLogger().info("TheGoldEconomy disabled.");
  }
}
