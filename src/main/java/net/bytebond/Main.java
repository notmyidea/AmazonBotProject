package net.bytebond;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main implements EventListener {

	private String productName = "";

	public static void main(String[] args) throws InterruptedException {
	String token = getTokenFromConfig();
	if (token.isEmpty()) {System.out.println("Token is empty."); return; }


		JDA jda = JDABuilder.createDefault(token)
				.addEventListeners(new Main())
				.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS)
				.setActivity(Activity.playing("AmazonBot"))
				.build();

		jda.awaitReady();

		jda.upsertCommand("pricing", "Get the price of an item on Amazon").addOption(OptionType.STRING, "links", "The Amazon product links", true).queue();
		System.out.println("Commands registered.");
	}

	public static String getTokenFromConfig() {
		String token = "";
		Path configPath = Paths.get("./config.yml");

		try {
			if (!Files.exists(configPath)) {
				String defaultConfig = "token: YOUR-BOT-TOKEN\n";
				Files.write(configPath, defaultConfig.getBytes());
			}

			Properties prop = new Properties();
			prop.load(Files.newInputStream(configPath));
			token = prop.getProperty("token");
		} catch (IOException e) {
			System.out.println("An error occurred while reading or creating the config file.");
			e.printStackTrace();
		}

		return token;
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof ReadyEvent) {
			System.out.println("Bot is ready, waiting for events...");
			System.out.println("Java Version: " + System.getProperty("java.version"));
		} else if (event instanceof MessageReceivedEvent) {
			onMessageReceived((MessageReceivedEvent) event);
		} else if (event instanceof SlashCommandInteractionEvent) {
			onSlashCommand((SlashCommandInteractionEvent) event);
		}
	}

	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.isFromType(ChannelType.PRIVATE))
		{
			System.out.printf("[PM] %s: %s\n", event.getAuthor().getName(),
					event.getMessage().getContentDisplay());
		}
		else
		{
			System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(),
					event.getChannel().getName(), Objects.requireNonNull(event.getMember()).getEffectiveName(),
					event.getMessage().getContentDisplay());
		}
	}

	public void onSlashCommand(SlashCommandInteractionEvent event) {
		System.out.printf("[SlashCommand] %s: %s\n", Objects.requireNonNull(event.getMember()).getEffectiveName(),
				event.getCommandType());
	  // DELETED AN UNNECESSARY .toString() ^^^ BEHIND .getCommandType() ^^^
		if(event.getName().equals("pricing")) {
			String links = Objects.requireNonNull(event.getOption("links")).getAsString();

			event.reply("Gathering the prices, this could take a while...").queue();

			new Thread(() -> {
				String[] linkArray = links.split(" ");
				double total = 0;
				StringBuilder validLinks = new StringBuilder();
				StringBuilder failedLinks = new StringBuilder();
				for (String link : linkArray) {
					Map<String, String> prices = SeleniumFetcher(new String[]{link});
					String price = prices.get(link);
					if (price == null) {
						failedLinks.append(String.format("[Click](%s) ", link));
					} else {
						total += Double.parseDouble(price.replace("$", "").replace(" ", ""));
						validLinks.append(String.format("[Click](%s) ", link));
					}
				}

				double discountedTotal = total * 0.2;
				String formattedTotal = String.format("%.2f", total);
				String formattedDiscountedTotal = String.format("%.2f", discountedTotal);

				EmbedBuilder embedBuilder = new EmbedBuilder();

				if (linkArray.length > 1) {
					embedBuilder.setTitle("Pricing Information");
				} else { //weird error about it being null huh
					if(productName.isEmpty()) {
						productName = "Product";
					}
					embedBuilder.setTitle(productName);
				}

				int numberOfProducts = linkArray.length;
				embedBuilder.addField("Number of Products", String.valueOf(numberOfProducts), false);
				embedBuilder.addField("Total Price", "$" + formattedTotal, false);
				embedBuilder.addField("20% Discounted Price", "$" + formattedDiscountedTotal, false);


				if (validLinks.length() > 0) {
					embedBuilder.addField("Links", validLinks.toString(), false);
				}

				if (failedLinks.length() > 0) {
					embedBuilder.addField("Failed Links", failedLinks.toString(), false);
				}

				embedBuilder.setThumbnail("https://upload.wikimedia.org/wikipedia/commons/thumb/4/4a/Amazon_icon.svg/2500px-Amazon_icon.svg.png");


			// last addition

				event.getChannel().sendMessage(event.getUser().getAsMention()).setEmbeds(embedBuilder.build()).queue();
				System.out.println("Message sent.");
				System.out.println("EVENT STOPPED");
			}).start();
		}
	}

	public Map<String, String> SeleniumFetcher(String [] links) {
		Map<String, String> prices = new HashMap<>();
		String driverPath = "lib/chromedriver-win64/chromedriver.exe";
		System.setProperty("webdriver.chrome.driver", driverPath);

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless");
		//WebDriver driver = new ChromeDriver(options);

		//  ^ v --> OPTIMIZATION

		// DISABLE IMAGES
		Map<String, Object> prefs = new HashMap<>();
		prefs.put("profile.managed_default_content_settings.images", 2);

		// DISABLE JAVASCRIPT <-- SCREW JAVASCRIPT AAAAHH
		prefs.put("profile.managed_default_content_settings.javascript", 2);
		options.setExperimentalOption("prefs", prefs);

		WebDriver driver = new ChromeDriver(options);

		for (String link : links) {

			System.out.println("ChromeDriver starting up... ");
			System.out.println("Link: " + link);

			if (!link.contains("amazon")) {
				System.out.println("Invalid link: " + link);
				continue;
			}

			driver.get(link);

			try {
				WebElement currencySymbolElement = driver.findElement(By.className("a-price-symbol"));
				String currencySymbol = currencySymbolElement.getText().trim();

				WebElement priceWholeElement = driver.findElement(By.className("a-price-whole"));
				String priceWhole = priceWholeElement.getText().trim().replace(",", "");

				WebElement priceFractionElement = driver.findElement(By.className("a-price-fraction"));
				String priceFraction = priceFractionElement.getText().trim();

				String price = priceWhole + "." + priceFraction;

				if (currencySymbol.equals("$")) currencySymbol = "USD";
				if (currencySymbol.equals("€")) currencySymbol = "EUR";
				if (currencySymbol.equals("£")) currencySymbol = "GBP";

				System.out.println("Currency: " + currencySymbol);
				System.out.println("Price: " + currencySymbol + price);

				String productName = driver.getTitle().substring(11);
				if (productName.length() > 20) productName = productName.substring(0, 31);
				System.out.println("Product name: " + productName);

				double discountedPrice = Double.parseDouble(price) * 0.2;
				System.out.println("Discounted Price: " + currencySymbol + discountedPrice);
				prices.put(link, price);
			} catch (Exception e) {
				System.out.println("Error: Could not retrieve price for " + link);
			}
		}

		driver.quit();

		return prices;
	}





}



