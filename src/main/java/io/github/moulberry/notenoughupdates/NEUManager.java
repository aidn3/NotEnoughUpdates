/*
 * Copyright (C) 2022 NotEnoughUpdates contributors
 *
 * This file is part of NotEnoughUpdates.
 *
 * NotEnoughUpdates is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * NotEnoughUpdates is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with NotEnoughUpdates. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.moulberry.notenoughupdates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.moulberry.notenoughupdates.auction.APIManager;
import io.github.moulberry.notenoughupdates.events.RepositoryReloadEvent;
import io.github.moulberry.notenoughupdates.miscgui.GuiItemRecipe;
import io.github.moulberry.notenoughupdates.miscgui.KatSitterOverlay;
import io.github.moulberry.notenoughupdates.recipes.CraftingOverlay;
import io.github.moulberry.notenoughupdates.recipes.CraftingRecipe;
import io.github.moulberry.notenoughupdates.recipes.Ingredient;
import io.github.moulberry.notenoughupdates.recipes.NeuRecipe;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.HotmInformation;
import io.github.moulberry.notenoughupdates.util.HypixelApi;
import io.github.moulberry.notenoughupdates.util.SBInfo;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.FileUtils;
import org.lwjgl.input.Keyboard;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NEUManager {
	private final NotEnoughUpdates neu;
	public final Gson gson;
	public final APIManager auctionManager;

	private final TreeMap<String, JsonObject> itemMap = new TreeMap<>();
	private boolean hasBeenLoadedBefore = false;

	private final TreeMap<String, HashMap<String, List<Integer>>> titleWordMap = new TreeMap<>();
	private final TreeMap<String, HashMap<String, List<Integer>>> loreWordMap = new TreeMap<>();

	public final KeyBinding keybindGive =
		new KeyBinding("Add item to inventory (Creative-only)", Keyboard.KEY_L, "NotEnoughUpdates");
	public final KeyBinding keybindFavourite =
		new KeyBinding("Set item as favourite", Keyboard.KEY_F, "NotEnoughUpdates");
	public final KeyBinding keybindViewUsages =
		new KeyBinding("Show usages for item", Keyboard.KEY_U, "NotEnoughUpdates");
	public final KeyBinding keybindViewRecipe =
		new KeyBinding("Show recipe for item", Keyboard.KEY_R, "NotEnoughUpdates");
	public final KeyBinding keybindToggleDisplay = new KeyBinding("Toggle NEU overlay", 0, "NotEnoughUpdates");
	public final KeyBinding keybindClosePanes = new KeyBinding("Close NEU panes", 0, "NotEnoughUpdates");
	public final KeyBinding keybindItemSelect = new KeyBinding("Select Item", -98 /*middle*/, "NotEnoughUpdates");
	public final KeyBinding[] keybinds = new KeyBinding[]{
		keybindGive, keybindFavourite, keybindViewUsages, keybindViewRecipe,
		keybindToggleDisplay, keybindClosePanes, keybindItemSelect
	};

	public String viewItemAttemptID = null;
	public long viewItemAttemptTime = 0;

	private final String currentProfile = "";
	private final String currentProfileBackup = "";
	public final HypixelApi hypixelApi = new HypixelApi();

	private final Map<String, ItemStack> itemstackCache = new HashMap<>();

	private final Set<NeuRecipe> recipes = new HashSet<>();
	private final HashMap<String, Set<NeuRecipe>> recipesMap = new HashMap<>();
	private final HashMap<String, Set<NeuRecipe>> usagesMap = new HashMap<>();

	public String latestRepoCommit = null;

	public File configLocation;
	public File repoLocation;
	public File configFile;
	public HotmInformation hotm;

	public KatSitterOverlay katSitterOverlay;

	public CraftingOverlay craftingOverlay;

	public NEUManager(NotEnoughUpdates neu, File configLocation) {
		this.neu = neu;
		this.configLocation = configLocation;
		this.auctionManager = new APIManager(this);
		this.hotm = new HotmInformation(neu);
		this.craftingOverlay = new CraftingOverlay(this);
		this.katSitterOverlay = new KatSitterOverlay();

		gson = new GsonBuilder().setPrettyPrinting().create();

		this.repoLocation = new File(configLocation, "repo");
		repoLocation.mkdir();
	}

	public void setCurrentProfile(String currentProfile) {
		SBInfo.getInstance().currentProfile = currentProfile;
	}

	public String getCurrentProfile() {
		return SBInfo.getInstance().currentProfile;
	}

	public <T> T getJsonFromFile(File file, Class<T> clazz) {
		try (
			BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(file),
				StandardCharsets.UTF_8
			))
		) {
			T obj = gson.fromJson(reader, clazz);
			return obj;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Parses a file in to a JsonObject.
	 */
	public JsonObject getJsonFromFile(File file) {
		try (
			BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(file),
				StandardCharsets.UTF_8
			))
		) {
			JsonObject json = gson.fromJson(reader, JsonObject.class);
			return json;
		} catch (Exception e) {
			return null;
		}
	}

	public void resetRepo() {
		try {
			Utils.recursiveDelete(new File(configLocation, "repo"));
		} catch (Exception ignored) {
		}
		try {
			new File(configLocation, "currentCommit.json").delete();
		} catch (Exception ignored) {
		}
	}

	public CompletableFuture<Boolean> fetchRepository() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				JsonObject currentCommitJSON = getJsonFromFile(new File(configLocation, "currentCommit.json"));

				latestRepoCommit = null;
				try (Reader inReader = new InputStreamReader(new URL(neu.config.apiData.getCommitApiUrl()).openStream())) {
					JsonObject commits = gson.fromJson(inReader, JsonObject.class);
					latestRepoCommit = commits.get("sha").getAsString();
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (latestRepoCommit == null || latestRepoCommit.isEmpty()) return false;

				if (new File(configLocation, "repo").exists() && new File(configLocation, "repo/items").exists()) {
					if (currentCommitJSON != null && currentCommitJSON.get("sha").getAsString().equals(latestRepoCommit)) {
						return false;
					}
				}
				Utils.recursiveDelete(repoLocation);
				repoLocation.mkdirs();

				File itemsZip = new File(repoLocation, "neu-items-master.zip");
				try {
					itemsZip.createNewFile();
				} catch (IOException e) {
					return false;
				}

				URL url = new URL(neu.config.apiData.getDownloadUrl(latestRepoCommit));
				URLConnection urlConnection = url.openConnection();
				urlConnection.setConnectTimeout(15000);
				urlConnection.setReadTimeout(30000);

				try (InputStream is = urlConnection.getInputStream()) {
					FileUtils.copyInputStreamToFile(is, itemsZip);
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("Failed to download NEU Repo! Please report this issue to the mod creator");
					return false;
				}

				unzipIgnoreFirstFolder(itemsZip.getAbsolutePath(), repoLocation.getAbsolutePath());

				if (currentCommitJSON == null || !currentCommitJSON.get("sha").getAsString().equals(latestRepoCommit)) {
					JsonObject newCurrentCommitJSON = new JsonObject();
					newCurrentCommitJSON.addProperty("sha", latestRepoCommit);
					try {
						writeJson(newCurrentCommitJSON, new File(configLocation, "currentCommit.json"));
					} catch (IOException ignored) {
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return true;
		});
	}

	/**
	 * Called when the game is first loaded. Compares the local repository to the github repository and handles the
	 * downloading of new/updated files. This then calls the "loadItem" method for every item in the local repository.
	 */
	public void loadItemInformation() {
		if (NotEnoughUpdates.INSTANCE.config.apiData.autoupdate) {
			fetchRepository().thenRun(this::reloadRepository);
		} else {
			reloadRepository();
		}
	}

	/**
	 * Loads the item in to the itemMap and also stores various words associated with this item in to titleWordMap and
	 * loreWordMap. These maps are used in the searching algorithm.
	 */
	public void loadItem(String internalName) {
		itemstackCache.remove(internalName);
		try {
			JsonObject json = getJsonFromFile(new File(new File(repoLocation, "items"), internalName + ".json"));
			if (json == null) {
				return;
			}

			if (json.get("itemid") == null) return;

			String itemid = json.get("itemid").getAsString();
			Item mcitem = Item.getByNameOrId(itemid);
			if (mcitem != null) {
				itemid = mcitem.getRegistryName();
			}
			json.addProperty("itemid", itemid);

			itemMap.put(internalName, json);

			if (json.has("recipe")) {
				JsonObject recipe = json.getAsJsonObject("recipe");
				NeuRecipe neuRecipe = NeuRecipe.parseRecipe(this, recipe, json);
				if (neuRecipe != null)
					registerNeuRecipe(neuRecipe);
			}
			if (json.has("recipes")) {
				for (JsonElement element : json.getAsJsonArray("recipes")) {
					JsonObject recipe = element.getAsJsonObject();
					NeuRecipe neuRecipe = NeuRecipe.parseRecipe(this, recipe, json);
					if (neuRecipe != null)
						registerNeuRecipe(neuRecipe);
				}
			}

			if (json.has("displayname")) {
				synchronized (titleWordMap) {
					int wordIndex = 0;
					for (String str : json.get("displayname").getAsString().split(" ")) {
						str = clean(str);
						if (!titleWordMap.containsKey(str)) {
							titleWordMap.put(str, new HashMap<>());
						}
						if (!titleWordMap.get(str).containsKey(internalName)) {
							titleWordMap.get(str).put(internalName, new ArrayList<>());
						}
						titleWordMap.get(str).get(internalName).add(wordIndex);
						wordIndex++;
					}
				}
			}

			if (json.has("lore")) {
				synchronized (loreWordMap) {
					int wordIndex = 0;
					for (JsonElement element : json.get("lore").getAsJsonArray()) {
						for (String str : element.getAsString().split(" ")) {
							str = clean(str);
							if (!loreWordMap.containsKey(str)) {
								loreWordMap.put(str, new HashMap<>());
							}
							if (!loreWordMap.get(str).containsKey(internalName)) {
								loreWordMap.get(str).put(internalName, new ArrayList<>());
							}
							loreWordMap.get(str).get(internalName).add(wordIndex);
							wordIndex++;
						}
					}
				}
			}
		} catch (Exception e) {
			synchronized (loreWordMap) {
				System.out.println("loreWordMap is : " + loreWordMap);
			}
			synchronized (titleWordMap) {
				System.out.println("titleWordMap is : " + titleWordMap);
			}
			System.out.println("internalName is : " + internalName);
			e.printStackTrace();
		}
	}

	public void registerNeuRecipe(NeuRecipe recipe) {
		recipes.add(recipe);
		for (Ingredient output : recipe.getOutputs()) {
			recipesMap.computeIfAbsent(output.getInternalItemId(), ignored -> new HashSet<>()).add(recipe);
		}
		for (Ingredient input : recipe.getIngredients()) {
			usagesMap.computeIfAbsent(input.getInternalItemId(), ignored -> new HashSet<>()).add(recipe);
		}
		for (Ingredient catalystItem : recipe.getCatalystItems()) {
			recipesMap.computeIfAbsent(catalystItem.getInternalItemId(), ignored -> new HashSet<>()).add(recipe);
			usagesMap.computeIfAbsent(catalystItem.getInternalItemId(), ignored -> new HashSet<>()).add(recipe);
		}
	}

	public Set<NeuRecipe> getRecipesFor(String internalName) {
		return recipesMap.getOrDefault(internalName, Collections.emptySet());
	}

	public List<NeuRecipe> getAvailableRecipesFor(String internalname) {
		return getRecipesFor(internalname).stream().filter(NeuRecipe::isAvailable).collect(Collectors.toList());
	}

	public Set<NeuRecipe> getUsagesFor(String internalName) {
		return usagesMap.getOrDefault(internalName, Collections.emptySet());
	}

	public List<NeuRecipe> getAvailableUsagesFor(String internalname) {
		return getUsagesFor(internalname).stream().filter(NeuRecipe::isAvailable).collect(Collectors.toList());
	}

	/**
	 * Searches a string for a query. This method is used to mimic the behaviour of the more complex map-based search
	 * function. This method is used for the chest-item-search feature.
	 */
	public boolean searchString(String toSearch, String query) {
		int lastMatch = -1;

		toSearch = clean(toSearch).toLowerCase();
		query = clean(query).toLowerCase();
		String[] splitToSeach = toSearch.split(" ");
		out:
		for (String s : query.split(" ")) {
			for (int i = 0; i < splitToSeach.length; i++) {
				if (!(lastMatch == -1 || lastMatch == i - 1)) continue;
				if (splitToSeach[i].startsWith(s)) {
					lastMatch = i;
					continue out;
				}
			}
			return false;
		}

		return true;
	}

	/**
	 * Checks whether an itemstack matches a certain query, following the same rules implemented by the more complex
	 * map-based search function.
	 */
	public boolean doesStackMatchSearch(ItemStack stack, String query) {
		if (query.startsWith("title:")) {
			query = query.substring(6);
			return searchString(stack.getDisplayName(), query);
		} else if (query.startsWith("desc:")) {
			query = query.substring(5);
			String lore = "";
			NBTTagCompound tag = stack.getTagCompound();
			if (tag != null) {
				NBTTagCompound display = tag.getCompoundTag("display");
				if (display.hasKey("Lore", 9)) {
					NBTTagList list = display.getTagList("Lore", 8);
					for (int i = 0; i < list.tagCount(); i++) {
						lore += list.getStringTagAt(i) + " ";
					}
				}
			}
			return searchString(lore, query);
		} else if (query.startsWith("id:")) {
			query = query.substring(3);
			String internalName = getInternalNameForItem(stack);
			return query.equalsIgnoreCase(internalName);
		} else {
			boolean result = false;
			if (!query.trim().contains(" ")) {
				StringBuilder sb = new StringBuilder();
				for (char c : query.toCharArray()) {
					sb.append(c).append(" ");
				}
				result = result || searchString(stack.getDisplayName(), sb.toString());
			}
			result = result || searchString(stack.getDisplayName(), query);

			String lore = "";
			NBTTagCompound tag = stack.getTagCompound();
			if (tag != null) {
				NBTTagCompound display = tag.getCompoundTag("display");
				if (display.hasKey("Lore", 9)) {
					NBTTagList list = display.getTagList("Lore", 8);
					for (int i = 0; i < list.tagCount(); i++) {
						lore += list.getStringTagAt(i) + " ";
					}
				}
			}

			result = result || searchString(lore, query);

			return result;
		}
	}

	/**
	 * Calls search for each query, separated by | eg. search(A|B) = search(A) + search(B)
	 */
	public Set<String> search(String query, boolean multi) {
		if (multi) {
			Set<String> result = new HashSet<>();

			StringBuilder query2 = new StringBuilder();
			char lastOp = '|';
			for (char c : query.toCharArray()) {
				if (c == '|' || c == '&') {
					if (lastOp == '|') {
						result.addAll(search(query2.toString()));
					} else if (lastOp == '&') {
						result.retainAll(search(query2.toString()));
					}

					query2 = new StringBuilder();
					lastOp = c;
				} else {
					query2.append(c);
				}
			}
			if (lastOp == '|') {
				result.addAll(search(query2.toString()));
			} else if (lastOp == '&') {
				result.retainAll(search(query2.toString()));
			}

			return result;
		} else {
			return search(query);
		}
	}

	/*public TreeMap<ItemStack> searchForStacks(String query, Set<ItemStack> stacks, boolean multi) {
		if (multi) {
			Set<String> result = new HashSet<>();

			StringBuilder query2 = new StringBuilder();
			char lastOp = '|';
			for (char c : query.toCharArray()) {
				if (c == '|' || c == '&') {
					if (lastOp == '|') {
						result.addAll(doesStackMatchSearch(stack, query2.toString()));
					} else if (lastOp == '&') {
						result.retainAll(search(query2.toString()));
					}

					query2 = new StringBuilder();
					lastOp = c;
				} else {
					query2.append(c);
				}
			}
			if (lastOp == '|') {
				result.addAll(search(query2.toString()));
			} else if (lastOp == '&') {
				result.retainAll(search(query2.toString()));
			}

			return result;
		} else {
			return search(query);
		}
	}*/

	/**
	 * Returns the name of items which match a certain search query.
	 */
	public Set<String> search(String query) {
		query = query.trim();
		boolean negate = query.startsWith("!");
		if (negate) query = query.substring(1);

		LinkedHashSet<String> results = new LinkedHashSet<>();
		if (query.startsWith("title:")) {
			query = query.substring(6);
			results.addAll(new TreeSet<>(search(query, titleWordMap)));
		} else if (query.startsWith("desc:")) {
			query = query.substring(5);
			results.addAll(new TreeSet<>(search(query, loreWordMap)));
		} else if (query.startsWith("id:")) {
			query = query.substring(3);
			results.addAll(new TreeSet<>(subMapWithKeysThatAreSuffixes(query.toUpperCase(), itemMap).keySet()));
		} else {
			if (!query.trim().contains(" ")) {
				StringBuilder sb = new StringBuilder();
				for (char c : query.toCharArray()) {
					sb.append(c).append(" ");
				}
				results.addAll(new TreeSet<>(search(sb.toString(), titleWordMap)));
			}
			results.addAll(new TreeSet<>(search(query, titleWordMap)));
			results.addAll(new TreeSet<>(search(query, loreWordMap)));
		}
		if (!negate) {
			return results;
		} else {
			Set<String> negatedResults = new HashSet<>();
			for (String internalname : itemMap.keySet()) {
				negatedResults.add(internalname);
			}
			negatedResults.removeAll(results);
			return negatedResults;
		}
	}

	/**
	 * Splits a search query into an array of strings delimited by a space character. Then, matches the query to the
	 * start of words in the various maps (title & lore). The small query does not need to match the whole entry of the
	 * map, only the beginning. eg. "ench" and "encha" will both match "enchanted". All sub queries must follow a word
	 * matching the previous sub query. eg. "ench po" will match "enchanted pork" but will not match "pork enchanted".
	 */
	public Set<String> search(String query, TreeMap<String, HashMap<String, List<Integer>>> wordMap) {
		HashMap<String, List<Integer>> matches = null;

		query = clean(query).toLowerCase();
		for (String queryWord : query.split(" ")) {
			HashMap<String, List<Integer>> matchesToKeep = new HashMap<>();
			for (HashMap<String, List<Integer>> wordMatches : subMapWithKeysThatAreSuffixes(queryWord, wordMap).values()) {
				if (!(wordMatches != null && !wordMatches.isEmpty())) continue;
				if (matches == null) {
					//Copy all wordMatches to titleMatches
					for (String internalname : wordMatches.keySet()) {
						if (!matchesToKeep.containsKey(internalname)) {
							matchesToKeep.put(internalname, new ArrayList<>());
						}
						matchesToKeep.get(internalname).addAll(wordMatches.get(internalname));
					}
				} else {
					for (String internalname : matches.keySet()) {
						if (!wordMatches.containsKey(internalname)) continue;
						for (Integer newIndex : wordMatches.get(internalname)) {
							if (!matches.get(internalname).contains(newIndex - 1)) continue;
							if (!matchesToKeep.containsKey(internalname)) {
								matchesToKeep.put(internalname, new ArrayList<>());
							}
							matchesToKeep.get(internalname).add(newIndex);
						}
					}
				}
			}
			if (matchesToKeep.isEmpty()) return new HashSet<>();
			matches = matchesToKeep;
		}

		return matches.keySet();
	}

	/**
	 * From https://stackoverflow.com/questions/10711494/get-values-in-treemap-whose-string-keys-start-with-a-pattern
	 */
	public <T> Map<String, T> subMapWithKeysThatAreSuffixes(String prefix, NavigableMap<String, T> map) {
		if ("".equals(prefix)) return map;
		String lastKey = createLexicographicallyNextStringOfTheSameLength(prefix);
		return map.subMap(prefix, true, lastKey, false);
	}

	public String createLexicographicallyNextStringOfTheSameLength(String input) {
		final int lastCharPosition = input.length() - 1;
		String inputWithoutLastChar = input.substring(0, lastCharPosition);
		char lastChar = input.charAt(lastCharPosition);
		char incrementedLastChar = (char) (lastChar + 1);
		return inputWithoutLastChar + incrementedLastChar;
	}

	public JsonObject getJsonFromItemBytes(String item_bytes) {
		try {
			NBTTagCompound tag =
				CompressedStreamTools.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(item_bytes)));
			//System.out.println(tag.toString());
			return getJsonFromNBT(tag);
		} catch (IOException e) {
			return null;
		}
	}

	public String getUUIDFromNBT(NBTTagCompound tag) {
		String uuid = null;
		if (tag != null && tag.hasKey("ExtraAttributes", 10)) {
			NBTTagCompound ea = tag.getCompoundTag("ExtraAttributes");

			if (ea.hasKey("uuid", 8)) {
				uuid = ea.getString("uuid");
			}
		}
		return uuid;
	}

	public String getSkullValueFromNBT(NBTTagCompound tag) {
		if (tag != null && tag.hasKey("SkullOwner", 10)) {
			NBTTagCompound ea = tag.getCompoundTag("SkullOwner");
			NBTTagCompound ea3 = tag.getCompoundTag("display");

			if (ea.hasKey("Properties", 10)) {
				NBTTagCompound ea2 = ea;
				ea = ea.getCompoundTag("Properties");
				ea = ea.getTagList("textures", 10).getCompoundTagAt(0);
				String name = ea3.getString("Name").replaceAll(" M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$", "");
				return "put(\"ID\", Utils.createSkull(EnumChatFormatting.AQUA + \"" + name + "\" ,\"" + ea2.getString("Id") +
					"\", \"" + ea.getString("Value") + "\"));";
			}
		}
		return null;
	}

	public String getInternalnameFromNBT(NBTTagCompound tag) {
		String internalname = null;
		if (tag != null && tag.hasKey("ExtraAttributes", 10)) {
			NBTTagCompound ea = tag.getCompoundTag("ExtraAttributes");

			if (ea.hasKey("id", 8)) {
				internalname = ea.getString("id").replaceAll(":", "-");
			} else {
				return null;
			}

			if ("PET".equals(internalname)) {
				String petInfo = ea.getString("petInfo");
				if (petInfo.length() > 0) {
					JsonObject petInfoObject = gson.fromJson(petInfo, JsonObject.class);
					internalname = petInfoObject.get("type").getAsString();
					String tier = petInfoObject.get("tier").getAsString();
					switch (tier) {
						case "COMMON":
							internalname += ";0";
							break;
						case "UNCOMMON":
							internalname += ";1";
							break;
						case "RARE":
							internalname += ";2";
							break;
						case "EPIC":
							internalname += ";3";
							break;
						case "LEGENDARY":
							internalname += ";4";
							break;
						case "MYTHIC":
							internalname += ";5";
							break;
					}
				}
			}
			if ("ENCHANTED_BOOK".equals(internalname) && ea.hasKey("enchantments", 10)) {
				NBTTagCompound enchants = ea.getCompoundTag("enchantments");

				for (String enchname : enchants.getKeySet()) {
					internalname = enchname.toUpperCase() + ";" + enchants.getInteger(enchname);
					break;
				}
			}
			if ("RUNE".equals(internalname) && ea.hasKey("runes", 10)) {
				NBTTagCompound rune = ea.getCompoundTag("runes");

				for (String runename : rune.getKeySet()) {
					internalname = runename.toUpperCase() + "_RUNE" + ";" + rune.getInteger(runename);
					break;
				}
			}
			if ("PARTY_HAT_CRAB".equals(internalname) && (ea.getString("party_hat_color") != null)) {
				String crabhat = ea.getString("party_hat_color");
				internalname = "PARTY_HAT_CRAB" + "_" + crabhat.toUpperCase();
			}
		}

		return internalname;
	}

	public String[] getLoreFromNBT(NBTTagCompound tag) {
		String[] lore = new String[0];
		NBTTagCompound display = tag.getCompoundTag("display");

		if (display.hasKey("Lore", 9)) {
			NBTTagList list = display.getTagList("Lore", 8);
			lore = new String[list.tagCount()];
			for (int k = 0; k < list.tagCount(); k++) {
				lore[k] = list.getStringTagAt(k);
			}
		}
		return lore;
	}

	public JsonObject getJsonFromNBT(NBTTagCompound tag) {
		return getJsonFromNBTEntry(tag.getTagList("i", 10).getCompoundTagAt(0));
	}

	public JsonObject getJsonFromNBTEntry(NBTTagCompound tag) {
		if (tag.getKeySet().size() == 0) return null;

		int id = tag.getShort("id");
		int damage = tag.getShort("Damage");
		int count = tag.getShort("Count");
		tag = tag.getCompoundTag("tag");

		if (id == 141) id = 391; //for some reason hypixel thinks carrots have id 141

		String internalname = getInternalnameFromNBT(tag);
		if (internalname == null) return null;

		NBTTagCompound display = tag.getCompoundTag("display");
		String[] lore = getLoreFromNBT(tag);

		Item itemMc = Item.getItemById(id);
		String itemid = "null";
		if (itemMc != null) {
			itemid = itemMc.getRegistryName();
		}
		String displayName = display.getString("Name");
		String[] info = new String[0];
		String clickcommand = "";

		JsonObject item = new JsonObject();
		item.addProperty("internalname", internalname);
		item.addProperty("itemid", itemid);
		item.addProperty("displayname", displayName);

		if (tag != null && tag.hasKey("ExtraAttributes", 10)) {
			NBTTagCompound ea = tag.getCompoundTag("ExtraAttributes");

			byte[] bytes = null;
			for (String key : ea.getKeySet()) {
				if (key.endsWith("backpack_data") || key.equals("new_year_cake_bag_data")) {
					bytes = ea.getByteArray(key);
					break;
				}
			}
			if (bytes != null) {
				JsonArray bytesArr = new JsonArray();
				for (byte b : bytes) {
					bytesArr.add(new JsonPrimitive(b));
				}
				item.add("item_contents", bytesArr);
			}
			if (ea.hasKey("dungeon_item_level")) {
				item.addProperty("dungeon_item_level", ea.getInteger("dungeon_item_level"));
			}
		}

		if (lore != null && lore.length > 0) {
			JsonArray jsonLore = new JsonArray();
			for (String line : lore) {
				jsonLore.add(new JsonPrimitive(line));
			}
			item.add("lore", jsonLore);
		}

		item.addProperty("damage", damage);
		if (count > 1) item.addProperty("count", count);
		item.addProperty("nbttag", tag.toString());

		return item;
	}

	private String clean(String str) {
		return str.replaceAll("(\u00a7.)|[^0-9a-zA-Z ]", "").toLowerCase().trim();
	}

	public void showRecipe(JsonObject item) {
		ContainerChest container = null;
		if (Minecraft.getMinecraft().thePlayer.openContainer instanceof ContainerChest)
			container = (ContainerChest) Minecraft.getMinecraft().thePlayer.openContainer;
		String internalName = item.get("internalname").getAsString();
		Set<NeuRecipe> recipesFor = getRecipesFor(internalName);
		if (container != null &&
			container.getLowerChestInventory().getDisplayName().getUnformattedText().equals("Craft Item")) {
			Optional<NeuRecipe> recipe = recipesFor.stream().filter(it -> it instanceof CraftingRecipe).findAny();
			if (recipe.isPresent()) {
				craftingOverlay.setShownRecipe((CraftingRecipe) recipe.get());
				return;
			}
		}
		if (!item.has("clickcommand")) return;
		String clickcommand = item.get("clickcommand").getAsString();
		switch (clickcommand.intern()) {
			case "viewrecipe":
				displayGuiItemRecipe(internalName);
				break;
			case "viewpotion":
				neu.sendChatMessage("/viewpotion " + internalName.split(";")[0].toLowerCase(Locale.ROOT));
		}
		displayGuiItemRecipe(internalName);
	}

	public void showRecipe(String internalName) {
		showRecipe(getItemInformation().get(internalName));
	}

	/**
	 * Takes an item stack and produces a JsonObject.
	 */
	public JsonObject getJsonForItem(ItemStack stack) {
		NBTTagCompound tag = stack.getTagCompound() == null ? new NBTTagCompound() : stack.getTagCompound();

		//Item lore
		String[] lore = new String[0];
		if (tag.hasKey("display", 10)) {
			NBTTagCompound display = tag.getCompoundTag("display");

			if (display.hasKey("Lore", 9)) {
				NBTTagList list = display.getTagList("Lore", 8);
				lore = new String[list.tagCount()];
				for (int i = 0; i < list.tagCount(); i++) {
					lore[i] = list.getStringTagAt(i);
				}
			}
		}

		if (stack.getDisplayName().endsWith(" Recipes")) {
			stack.setStackDisplayName(stack.getDisplayName().substring(0, stack.getDisplayName().length() - 8));
		}

		if (lore.length > 0 && (lore[lore.length - 1].contains("Click to view recipes!") ||
			lore[lore.length - 1].contains("Click to view recipe!"))) {
			String[] lore2 = new String[lore.length - 2];
			System.arraycopy(lore, 0, lore2, 0, lore.length - 2);
			lore = lore2;
		}

		JsonObject json = new JsonObject();
		json.addProperty("itemid", stack.getItem().getRegistryName());
		json.addProperty("displayname", stack.getDisplayName());
		json.addProperty("nbttag", tag.toString());
		json.addProperty("damage", stack.getItemDamage());

		JsonArray jsonlore = new JsonArray();
		for (String line : lore) {
			jsonlore.add(new JsonPrimitive(line));
		}
		json.add("lore", jsonlore);

		return json;
	}

	public String getSkullValueForItem(ItemStack stack) {
		if (stack == null) return null;
		NBTTagCompound tag = stack.getTagCompound();
		return getSkullValueFromNBT(tag);
	}

	public String getInternalNameForItem(ItemStack stack) {
		if (stack == null) return null;
		NBTTagCompound tag = stack.getTagCompound();
		return getInternalnameFromNBT(tag);
	}

	public String getUUIDForItem(ItemStack stack) {
		if (stack == null) return null;
		NBTTagCompound tag = stack.getTagCompound();
		return getUUIDFromNBT(tag);
	}

	public void writeItemToFile(ItemStack stack) {
		String internalname = getInternalNameForItem(stack);

		if (internalname == null) {
			return;
		}

		JsonObject json = getJsonForItem(stack);
		json.addProperty("internalname", internalname);
		json.addProperty("clickcommand", "");
		json.addProperty("modver", NotEnoughUpdates.VERSION);

		try {
			writeJson(json, new File(new File(repoLocation, "items"), internalname + ".json"));
		} catch (IOException ignored) {
		}

		loadItem(internalname);
	}

	public boolean displayGuiItemUsages(String internalName) {
		if (!usagesMap.containsKey(internalName)) return false;
		List<NeuRecipe> usages = getAvailableUsagesFor(internalName);
		if (usages.isEmpty()) return false;
		NotEnoughUpdates.INSTANCE.openGui = (new GuiItemRecipe(usages, this));
		return true;
	}

	public boolean displayGuiItemRecipe(String internalName) {
		if (!recipesMap.containsKey(internalName)) return false;
		List<NeuRecipe> recipes = getAvailableRecipesFor(internalName);
		if (recipes.isEmpty()) return false;
		NotEnoughUpdates.INSTANCE.openGui = (new GuiItemRecipe(recipes, this));
		return true;
	}

	/**
	 * Will display guiItemRecipe if a player attempted to view the recipe to an item but they didn't have the recipe
	 * unlocked. See NotEnoughUpdates#onGuiChat for where this method is called.
	 */
	public boolean failViewItem(String text) {
		if (viewItemAttemptID != null && !viewItemAttemptID.isEmpty()) {
			if (System.currentTimeMillis() - viewItemAttemptTime < 500) {
				return displayGuiItemRecipe(viewItemAttemptID);
			}
		}
		return false;
	}

	/**
	 * Downloads a web file, appending some HTML attributes that makes wikia give us the raw wiki syntax.
	 */
	public CompletableFuture<File> getWebFile(String url) {
		return CompletableFuture.supplyAsync(() -> {
			File f = new File(configLocation, "tmp/" + Base64.getEncoder().encodeToString(url.getBytes()) + ".html");
			if (f.exists()) {
				return f;
			}

			try {
				f.getParentFile().mkdirs();
				f.createNewFile();
				f.deleteOnExit();
			} catch (IOException e) {
				return null;
			}
			try {
				HttpsURLConnection con = (HttpsURLConnection) new URL(url + "?action=raw&templates=expand").openConnection();
				con.setRequestMethod("GET");
				con.setRequestProperty("User-Agent", "NotEnoughUpdates");
				BufferedInputStream inStream = new BufferedInputStream(con.getInputStream());
				FileOutputStream fileOutputStream = new FileOutputStream(f);
				byte[] dataBuffer = new byte[1024];
				int bytesRead;
				while ((bytesRead = inStream.read(dataBuffer, 0, 1024)) != -1) {
					fileOutputStream.write(dataBuffer, 0, bytesRead);
				}
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}

			return f;
		});
	}

	/**
	 * Modified from https://www.journaldev.com/960/java-unzip-file-example
	 */
	private static void unzipIgnoreFirstFolder(String zipFilePath, String destDir) {
		File dir = new File(destDir);
		// create output directory if it doesn't exist
		if (!dir.exists()) dir.mkdirs();
		FileInputStream fis;
		//buffer for read and write data to file
		byte[] buffer = new byte[1024];
		try {
			fis = new FileInputStream(zipFilePath);
			ZipInputStream zis = new ZipInputStream(fis);
			ZipEntry ze = zis.getNextEntry();
			while (ze != null) {
				if (!ze.isDirectory()) {
					String fileName = ze.getName();
					fileName = fileName.substring(fileName.split("/")[0].length() + 1);
					File newFile = new File(destDir + File.separator + fileName);
					//create directories for sub directories in zip
					new File(newFile.getParent()).mkdirs();
					if (!isInTree(dir, newFile)) {
						throw new RuntimeException(
							"Not Enough Updates detected an invalid zip file. This is a potential security risk, please report this in the Moulberry discord.");
					}
					FileOutputStream fos = new FileOutputStream(newFile);
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
				}
				//close this ZipEntry
				zis.closeEntry();
				ze = zis.getNextEntry();
			}
			//close last ZipEntry
			zis.closeEntry();
			zis.close();
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static boolean isInTree(File rootDirectory, File file) throws IOException {
		file = file.getCanonicalFile();
		rootDirectory = rootDirectory.getCanonicalFile();
		while (file != null) {
			if (file.equals(rootDirectory)) return true;
			file = file.getParentFile();
		}
		return false;
	}

	/**
	 * Modified from https://www.journaldev.com/960/java-unzip-file-example
	 */
	public static void unzip(InputStream src, File dest) {
		//buffer for read and write data to file
		byte[] buffer = new byte[1024];
		try {
			ZipInputStream zis = new ZipInputStream(src);
			ZipEntry ze = zis.getNextEntry();
			while (ze != null) {
				if (!ze.isDirectory()) {
					String fileName = ze.getName();
					File newFile = new File(dest, fileName);
					if (!isInTree(dest, newFile)) {
						throw new RuntimeException(
							"Not Enough Updates detected an invalid zip file. This is a potential security risk, please report this in the Moulberry discord.");
					}
					//create directories for sub directories in zip
					new File(newFile.getParent()).mkdirs();
					FileOutputStream fos = new FileOutputStream(newFile);
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
				}
				//close this ZipEntry
				zis.closeEntry();
				ze = zis.getNextEntry();
			}
			//close last ZipEntry
			zis.closeEntry();
			zis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * From here to the end of the file are various helper functions for creating and writing json files, in particular
	 * json files representing skyblock item data.
	 */
	public JsonObject createItemJson(
		String internalname, String itemid, String displayName, String[] lore,
		String crafttext, String infoType, String[] info,
		String clickcommand, int damage, NBTTagCompound nbttag
	) {
		return createItemJson(
			new JsonObject(),
			internalname,
			itemid,
			displayName,
			lore,
			crafttext,
			infoType,
			info,
			clickcommand,
			damage,
			nbttag
		);
	}

	public JsonObject createItemJson(
		JsonObject base, String internalname, String itemid, String displayName, String[] lore,
		String crafttext, String infoType, String[] info,
		String clickcommand, int damage, NBTTagCompound nbttag
	) {
		if (internalname == null || internalname.isEmpty()) {
			return null;
		}

		JsonObject json = gson.fromJson(gson.toJson(base, JsonObject.class), JsonObject.class);
		json.addProperty("internalname", internalname);
		json.addProperty("itemid", itemid);
		json.addProperty("displayname", displayName);
		json.addProperty("crafttext", crafttext);
		json.addProperty("clickcommand", clickcommand);
		json.addProperty("damage", damage);
		json.addProperty("nbttag", nbttag.toString());
		json.addProperty("modver", NotEnoughUpdates.VERSION);
		json.addProperty("infoType", infoType);

		if (info != null && info.length > 0) {
			JsonArray jsoninfo = new JsonArray();
			for (String line : info) {
				jsoninfo.add(new JsonPrimitive(line));
			}
			json.add("info", jsoninfo);
		}

		JsonArray jsonlore = new JsonArray();
		for (String line : lore) {
			jsonlore.add(new JsonPrimitive(line));
		}
		json.add("lore", jsonlore);

		return json;
	}

	public boolean writeItemJson(
		String internalname, String itemid, String displayName, String[] lore, String crafttext,
		String infoType, String[] info, String clickcommand, int damage, NBTTagCompound nbttag
	) {
		return writeItemJson(
			new JsonObject(),
			internalname,
			itemid,
			displayName,
			lore,
			crafttext,
			infoType,
			info,
			clickcommand,
			damage,
			nbttag
		);
	}

	public boolean writeItemJson(
		JsonObject base, String internalname, String itemid, String displayName, String[] lore,
		String crafttext, String infoType, String[] info, String clickcommand, int damage, NBTTagCompound nbttag
	) {
		JsonObject json = createItemJson(
			base,
			internalname,
			itemid,
			displayName,
			lore,
			crafttext,
			infoType,
			info,
			clickcommand,
			damage,
			nbttag
		);
		if (json == null) {
			return false;
		}

		try {
			writeJsonDefaultDir(json, internalname + ".json");
		} catch (IOException e) {
			return false;
		}

		loadItem(internalname);
		return true;
	}

	public void writeJson(JsonObject json, File file) throws IOException {
		file.createNewFile();

		try (
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(file),
				StandardCharsets.UTF_8
			))
		) {
			writer.write(gson.toJson(json));
		}
	}

	public void writeJsonDefaultDir(JsonObject json, String filename) throws IOException {
		File file = new File(new File(repoLocation, "items"), filename);
		writeJson(json, file);
	}

	public JsonObject readJsonDefaultDir(String filename) throws IOException {
		File f = new File(new File(repoLocation, "items"), filename);
		if (f.exists() && f.isFile() && f.canRead())
			try (Reader reader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
				return gson.fromJson(reader, JsonObject.class);
			} // rethrow io exceptions
		return null;
	}

	public TreeMap<String, JsonObject> getItemInformation() {
		return itemMap;
	}

	public String removeUnusedDecimal(double num) {
		if (num % 1 == 0) {
			return String.valueOf((int) num);
		} else {
			return String.valueOf(num);
		}
	}

	public HashMap<String, String> getLoreReplacements(String petname, String tier, int level) {
		JsonObject petnums = null;
		if (petname != null && tier != null) {
			petnums = Constants.PETNUMS;
		}

		HashMap<String, String> replacements = new HashMap<>();
		if (level < 1) {
			if (Constants.PETS != null && Constants.PETS.has("custom_pet_leveling") &&
				Constants.PETS.getAsJsonObject("custom_pet_leveling").has(petname) &&
				Constants.PETS.getAsJsonObject("custom_pet_leveling").getAsJsonObject(petname).has("max_level")) {
				int maxLvl =
					Constants.PETS.getAsJsonObject("custom_pet_leveling").getAsJsonObject(petname).get("max_level").getAsInt();
				replacements.put("LVL", "1\u27A1" + maxLvl);
			} else {
				replacements.put("LVL", "1\u27A1100");
			}
		} else {
			replacements.put("LVL", "" + level);
		}

		if (petnums != null) {
			if (petnums.has(petname)) {
				JsonObject petInfo = petnums.get(petname).getAsJsonObject();
				if (petInfo.has(tier)) {
					JsonObject petInfoTier = petInfo.get(tier).getAsJsonObject();
					if (petInfoTier == null || !petInfoTier.has("1") || !petInfoTier.has("100")) {
						return replacements;
					}

					JsonObject min = petInfoTier.get("1").getAsJsonObject();
					JsonObject max = petInfoTier.get("100").getAsJsonObject();

					if (level < 1) {
						JsonArray otherNumsMin = min.get("otherNums").getAsJsonArray();
						JsonArray otherNumsMax = max.get("otherNums").getAsJsonArray();
						boolean addZero = false;
						if (petInfoTier.has("stats_levelling_curve")) {
							String[] stringArray = petInfoTier.get("stats_levelling_curve").getAsString().split(":");
							if (stringArray.length == 3) {
								int type = Integer.parseInt(stringArray[2]);
								if (type == 1) {
									addZero = true;
								}
							}
						}
						for (int i = 0; i < otherNumsMax.size(); i++) {
							replacements.put(
								"" + i,
								(addZero ? "0\u27A1" : "") +
									removeUnusedDecimal(Math.floor(otherNumsMin.get(i).getAsFloat() * 10) / 10f) +
									"\u27A1" + removeUnusedDecimal(Math.floor(otherNumsMax.get(i).getAsFloat() * 10) / 10f)
							);
						}

						for (Map.Entry<String, JsonElement> entry : max.get("statNums").getAsJsonObject().entrySet()) {
							int statMax = (int) Math.floor(entry.getValue().getAsFloat());
							int statMin = (int) Math.floor(min.get("statNums").getAsJsonObject().get(entry.getKey()).getAsFloat());
							String statStr = (statMin > 0 ? "+" : "") + statMin + "\u27A1" + statMax;
							statStr = (addZero ? "0\u27A1" : "") + statStr;
							replacements.put(entry.getKey(), statStr);
						}
					} else {

						int minStatsLevel = 0;
						int maxStatsLevel = 100;
						int statsLevelingType = -1;

						int statsLevel = level;

						if (petInfoTier.has("stats_levelling_curve")) {
							String[] stringArray = petInfoTier.get("stats_levelling_curve").getAsString().split(":");
							if (stringArray.length == 3) {
								minStatsLevel = Integer.parseInt(stringArray[0]);
								maxStatsLevel = Integer.parseInt(stringArray[1]);
								statsLevelingType = Integer.parseInt(stringArray[2]);
								switch (statsLevelingType) {
									//Case for maybe a pet that might exist
									case 0:
									case 1:
										if (level < minStatsLevel) {
											statsLevel = 1;
										} else if (level < maxStatsLevel) {
											statsLevel = level - minStatsLevel + 1;
										} else {
											statsLevel = maxStatsLevel - minStatsLevel + 1;
										}
										break;

								}
							}
						}
						float minMix = (maxStatsLevel - (minStatsLevel - (statsLevelingType == -1 ? 0 : 1)) - statsLevel) / 99f;
						float maxMix = (statsLevel - 1) / 99f;

						JsonArray otherNumsMin = min.get("otherNums").getAsJsonArray();
						JsonArray otherNumsMax = max.get("otherNums").getAsJsonArray();
						for (int i = 0; i < otherNumsMax.size(); i++) {
							float val = otherNumsMin.get(i).getAsFloat() * minMix + otherNumsMax.get(i).getAsFloat() * maxMix;
							if (statsLevelingType == 1 && level < minStatsLevel) {
								replacements.put("" + i, "0");
							} else {
								replacements.put("" + i, removeUnusedDecimal(Math.floor(val * 10) / 10f));
							}
						}

						for (Map.Entry<String, JsonElement> entry : max.get("statNums").getAsJsonObject().entrySet()) {
							if (statsLevelingType == 1 && level < minStatsLevel) {
								replacements.put(entry.getKey(), "0");
							} else {
								float statMax = entry.getValue().getAsFloat();
								float statMin = min.get("statNums").getAsJsonObject().get(entry.getKey()).getAsFloat();
								float val = statMin * minMix + statMax * maxMix;
								String statStr = (statMin > 0 ? "+" : "") + (int) Math.floor(val);
								replacements.put(entry.getKey(), statStr);
							}
						}
					}
				}
			}
		}

		return replacements;
	}

	public HashMap<String, String> getLoreReplacements(NBTTagCompound tag, int level) {
		String petname = null;
		String tier = null;
		if (tag != null && tag.hasKey("ExtraAttributes")) {
			NBTTagCompound ea = tag.getCompoundTag("ExtraAttributes");
			if (ea.hasKey("petInfo")) {
				String petInfoStr = ea.getString("petInfo");
				JsonObject petInfo = gson.fromJson(petInfoStr, JsonObject.class);
				petname = petInfo.get("type").getAsString();
				tier = petInfo.get("tier").getAsString();
				if (petInfo.has("heldItem")) {
					String heldItem = petInfo.get("heldItem").getAsString();
					if (heldItem.equals("PET_ITEM_TIER_BOOST")) {
						switch (tier) {
							case "COMMON":
								tier = "UNCOMMON";
								break;
							case "UNCOMMON":
								tier = "RARE";
								break;
							case "RARE":
								tier = "EPIC";
								break;
							case "EPIC":
								tier = "LEGENDARY";
								break;
							case "LEGENDARY":
								tier = "MYTHIC";
								break;
						}
					}
				}
			}
		}
		return getLoreReplacements(petname, tier, level);
	}

	public NBTTagList processLore(JsonArray lore, HashMap<String, String> replacements) {
		NBTTagList nbtLore = new NBTTagList();
		for (JsonElement line : lore) {
			String lineStr = line.getAsString();
			if (!lineStr.contains("Click to view recipes!") &&
				!lineStr.contains("Click to view recipe!")) {
				for (Map.Entry<String, String> entry : replacements.entrySet()) {
					lineStr = lineStr.replace("{" + entry.getKey() + "}", entry.getValue());
				}
				nbtLore.appendTag(new NBTTagString(lineStr));
			}
		}
		return nbtLore;
	}

	public ItemStack jsonToStack(JsonObject json) {
		return jsonToStack(json, true);
	}

	public ItemStack jsonToStack(JsonObject json, boolean useCache) {
		return jsonToStack(json, useCache, true);
	}

	public ItemStack jsonToStack(JsonObject json, boolean useCache, boolean useReplacements) {
		return jsonToStack(json, useCache, useReplacements, true);
	}

	public ItemStack jsonToStack(JsonObject json, boolean useCache, boolean useReplacements, boolean copyStack) {
		if (json == null) return new ItemStack(Items.painting, 1, 10);
		String internalname = json.get("internalname").getAsString();

		if (useCache) {
			ItemStack stack = itemstackCache.get(internalname);
			if (stack != null) {
				if (copyStack) {
					return stack.copy();
				} else {
					return stack;
				}
			}
		}

		ItemStack stack = new ItemStack(Item.itemRegistry.getObject(
			new ResourceLocation(json.get("itemid").getAsString())));

		if (json.has("count")) {
			stack.stackSize = json.get("count").getAsInt();
		}

		if (stack.getItem() == null) {
			stack = new ItemStack(Item.getItemFromBlock(Blocks.stone), 0, 255); //Purple broken texture item
		} else {
			if (json.has("damage")) {
				stack.setItemDamage(json.get("damage").getAsInt());
			}

			if (json.has("nbttag")) {
				try {
					NBTTagCompound tag = JsonToNBT.getTagFromJson(json.get("nbttag").getAsString());
					stack.setTagCompound(tag);
				} catch (NBTException ignored) {
				}
			}

			HashMap<String, String> replacements = new HashMap<>();

			if (useReplacements) {
				replacements = getLoreReplacements(stack.getTagCompound(), -1);

				String displayName = json.get("displayname").getAsString();
				for (Map.Entry<String, String> entry : replacements.entrySet()) {
					displayName = displayName.replace("{" + entry.getKey() + "}", entry.getValue());
				}
				stack.setStackDisplayName(displayName);
			}

			if (json.has("lore")) {
				NBTTagCompound display = new NBTTagCompound();
				if (stack.getTagCompound() != null && stack.getTagCompound().hasKey("display")) {
					display = stack.getTagCompound().getCompoundTag("display");
				}
				display.setTag("Lore", processLore(json.get("lore").getAsJsonArray(), replacements));
				NBTTagCompound tag = stack.getTagCompound() != null ? stack.getTagCompound() : new NBTTagCompound();
				tag.setTag("display", display);
				stack.setTagCompound(tag);
			}
		}

		if (useCache) itemstackCache.put(internalname, stack);
		if (copyStack) {
			return stack.copy();
		} else {
			return stack;
		}
	}

	public CompletableFuture<List<String>> userFacingRepositoryReload() {
		String lastCommit = NotEnoughUpdates.INSTANCE.manager.latestRepoCommit;
		NotEnoughUpdates.INSTANCE.manager.resetRepo();
		return NotEnoughUpdates.INSTANCE.manager
			.fetchRepository()
			.thenCompose(ignored -> NotEnoughUpdates.INSTANCE.manager.reloadRepository())
			.thenApply(ignored -> {
				String newCommitHash = NotEnoughUpdates.INSTANCE.manager.latestRepoCommit;
				String newCommitShortHash = (newCommitHash == null ? "MISSING" : newCommitHash.substring(0, 7));
				return Arrays.asList(
					"§aRepository reloaded.",
					(lastCommit == null
						? "§eYou downloaded the repository version §b" + newCommitShortHash + "§e."
						: "§eYou updated your repository from §b" + lastCommit.substring(0, 7) + "§e to §b" +
							newCommitShortHash + "§e."
					)
				);
			})
			.exceptionally(ex -> {
				ex.printStackTrace();
				return Arrays.asList(
					"§cRepository not fully reloaded.",
					"§cThere was an error reloading your repository.",
					"§cThis might be caused by an outdated version of neu",
					"§c(or by not using the dangerous repository if you are using a prerelease of neu).",
					"§aYour repository will still work, but is in a suboptimal state.",
					"§eJoin §bdiscord.gg/moulberry §efor help."
				);
			});
	}

	public CompletableFuture<Void> reloadRepository() {
		CompletableFuture<Void> comp = new CompletableFuture<>();
		Minecraft.getMinecraft().addScheduledTask(() -> {
			try {
				File items = new File(repoLocation, "items");
				if (items.exists()) {
					recipes.clear();
					recipesMap.clear();
					usagesMap.clear();

					File[] itemFiles = new File(repoLocation, "items").listFiles();
					if (itemFiles != null) {
						for (File f : itemFiles) {
							String internalname = f.getName().substring(0, f.getName().length() - 5);
							loadItem(internalname);
						}
					}
				}

				new RepositoryReloadEvent(repoLocation, !hasBeenLoadedBefore).post();
				hasBeenLoadedBefore = true;
				comp.complete(null);
			} catch (Exception e) {
				comp.completeExceptionally(e);
			}
		});
		return comp;
	}

	public ItemStack createItem(String internalname) {
		JsonObject jsonObject = itemMap.get(internalname);
		if (jsonObject == null) return null;
		return jsonToStack(jsonObject);
	}
}
