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

package io.github.moulberry.notenoughupdates.profileviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.profileviewer.info.QuiverInfo;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer.pv_elements;

public class InventoriesPage extends GuiProfileViewerPage {

	public static final ResourceLocation pv_invs = new ResourceLocation("notenoughupdates:pv_invs.png");
	private static final Pattern DAMAGE_PATTERN = Pattern.compile("^Damage: \\+(\\d+)");
	private static final ResourceLocation CHEST_GUI_TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
	private static final Pattern STRENGTH_PATTERN = Pattern.compile("^Strength: \\+(\\d+)");
	private static final Pattern FISHSPEED_PATTERN = Pattern.compile("^Increases fishing speed by \\+(\\d+)");
	private static final LinkedHashMap<String, ItemStack> invNameToDisplayMap = new LinkedHashMap<String, ItemStack>() {
		{
			put("inv_contents", Utils.createItemStack(Item.getItemFromBlock(Blocks.chest), EnumChatFormatting.GRAY + "Inventory"));
			put(
				"ender_chest_contents",
				Utils.createItemStack(Item.getItemFromBlock(Blocks.ender_chest), EnumChatFormatting.GRAY + "Ender Chest")
			);
			// put("backpack_contents", Utils.createItemStack(Item.getItemFromBlock(Blocks.dropper), EnumChatFormatting.GRAY+"Backpacks"));
			put(
				"backpack_contents",
				Utils.editItemStackInfo(
					NotEnoughUpdates.INSTANCE.manager.jsonToStack(
						NotEnoughUpdates.INSTANCE.manager.getItemInformation().get("JUMBO_BACKPACK")
					),
					EnumChatFormatting.GRAY + "Backpacks",
					true
				)
			);
			put(
				"personal_vault_contents",
				Utils.editItemStackInfo(
					NotEnoughUpdates.INSTANCE.manager.jsonToStack(NotEnoughUpdates.INSTANCE.manager.getItemInformation().get("IRON_CHEST")),
					EnumChatFormatting.GRAY + "Personal Vault",
					true
				)
			);
			put("talisman_bag", Utils.createItemStack(Items.golden_apple, EnumChatFormatting.GRAY + "Accessory Bag"));
			put("wardrobe_contents", Utils.createItemStack(Items.leather_chestplate, EnumChatFormatting.GRAY + "Wardrobe"));
			put("fishing_bag", Utils.createItemStack(Items.fish, EnumChatFormatting.GRAY + "Fishing Bag"));
			put("potion_bag", Utils.createItemStack(Items.potionitem, EnumChatFormatting.GRAY + "Potion Bag"));
		}
	};
	private final ItemStack fillerStack = new ItemStack(Item.getItemFromBlock(Blocks.stained_glass_pane), 1, 15);
	private HashMap<String, ItemStack[][][]> inventoryItems = new HashMap<>();

	private ItemStack[] bestWeapons = null;
	private ItemStack[] bestRods = null;
	private ItemStack[] armorItems = null;
	private ItemStack[] equipmentItems = null;
	private String selectedInventory = "inv_contents";
	private int currentInventoryIndex = 0;
	private int arrowCount = -1;
	private int greenCandyCount = -1;
	private int purpleCandyCount = -1;

	public InventoriesPage(GuiProfileViewer instance) {
		super(instance);
	}

	@Override
	public void drawPage(int mouseX, int mouseY, float partialTicks) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		Minecraft.getMinecraft().getTextureManager().bindTexture(pv_invs);
		Utils.drawTexturedRect(guiLeft, guiTop, getInstance().sizeX, getInstance().sizeY, GL11.GL_NEAREST);
		getInstance().inventoryTextField.setSize(88, 20);

		ProfileViewer.Profile profile = GuiProfileViewer.getProfile();
		String profileId = GuiProfileViewer.getProfileId();
		JsonObject inventoryInfo = profile.getInventoryInfo(profileId);
		if (inventoryInfo == null) return;

		int invNameIndex = 0;
		for (Map.Entry<String, ItemStack> entry : invNameToDisplayMap.entrySet()) {
			int xIndex = invNameIndex % 3;
			int yIndex = invNameIndex / 3;

			int x = 19 + 34 * xIndex;
			int y = 26 + 34 * yIndex;

			Minecraft.getMinecraft().getTextureManager().bindTexture(pv_elements);
			if (entry.getKey().equals(selectedInventory)) {
				Utils.drawTexturedRect(guiLeft + x - 2, guiTop + y - 2, 20, 20, 20 / 256f, 0, 20 / 256f, 0, GL11.GL_NEAREST);
				x++;
				y++;
			} else {
				Utils.drawTexturedRect(guiLeft + x - 2, guiTop + y - 2, 20, 20, 0, 20 / 256f, 0, 20 / 256f, GL11.GL_NEAREST);
			}

			Utils.drawItemStackWithText(entry.getValue(), guiLeft + x, guiTop + y, "" + (invNameIndex + 1), true);

			if (mouseX >= guiLeft + x && mouseX <= guiLeft + x + 16) {
				if (mouseY >= guiTop + y && mouseY <= guiTop + y + 16) {
					getInstance().tooltipToDisplay = entry.getValue().getTooltip(Minecraft.getMinecraft().thePlayer, false);
					if (Objects.equals(entry.getKey(), "talisman_bag")) {
						StringBuilder magicalPowerString = new StringBuilder(EnumChatFormatting.DARK_GRAY + "Magical Power: ");
						int magicalPower = PlayerStats.getMagicalPower(inventoryInfo);
						getInstance()
							.tooltipToDisplay.add(
								magicalPower == -1
									? magicalPowerString.append(EnumChatFormatting.RED).append("Error while calculating!").toString()
									: magicalPowerString
										.append(EnumChatFormatting.GOLD)
										.append(GuiProfileViewer.numberFormat.format(magicalPower))
										.toString()
							);

						StringBuilder selectedPowerString = new StringBuilder(EnumChatFormatting.DARK_GRAY + "Selected Power: ");
						String selectedPower = PlayerStats.getSelectedMagicalPower(profile.getProfileInformation(profileId));
						getInstance()
							.tooltipToDisplay.add(
								selectedPower == null
									? selectedPowerString.append(EnumChatFormatting.RED).append("None!").toString()
									: selectedPowerString.append(EnumChatFormatting.GREEN).append(selectedPower).toString()
							);
					}
				}
			}

			invNameIndex++;
		}

		getInstance().inventoryTextField.render(guiLeft + 19, guiTop + getInstance().sizeY - 26 - 20);

		if (armorItems == null) {
			armorItems = new ItemStack[4];
			JsonArray armor = Utils.getElement(inventoryInfo, "inv_armor").getAsJsonArray();
			for (int i = 0; i < armor.size(); i++) {
				if (armor.get(i) == null || !armor.get(i).isJsonObject()) continue;
				armorItems[i] = NotEnoughUpdates.INSTANCE.manager.jsonToStack(armor.get(i).getAsJsonObject(), false);
			}
		}

		for (int i = 0; i < armorItems.length; i++) {
			ItemStack stack = armorItems[i];
			if (stack != null) {
				Utils.drawItemStack(stack, guiLeft + 173, guiTop + 67 - 18 * i, true);
				if (stack != fillerStack) {
					if (mouseX >= guiLeft + 173 - 1 && mouseX <= guiLeft + 173 + 16 + 1) {
						if (mouseY >= guiTop + 67 - 18 * i - 1 && mouseY <= guiTop + 67 - 18 * i + 16 + 1) {
							getInstance().tooltipToDisplay =
								stack.getTooltip(
									Minecraft.getMinecraft().thePlayer,
									Minecraft.getMinecraft().gameSettings.advancedItemTooltips
								);
						}
					}
				}
			}
		}

		if (equipmentItems == null) {
			equipmentItems = new ItemStack[4];
			JsonArray equippment = Utils.getElement(inventoryInfo, "equippment_contents").getAsJsonArray();
			for (int i = 0; i < equippment.size(); i++) {
				if (equippment.get(i) == null || !equippment.get(i).isJsonObject()) continue;
				equipmentItems[i] = NotEnoughUpdates.INSTANCE.manager.jsonToStack(equippment.get(i).getAsJsonObject(), false);
			}
		}

		for (int i = 0; i < equipmentItems.length; i++) {
			ItemStack stack = equipmentItems[i];
			if (stack != null) {
				Utils.drawItemStack(stack, guiLeft + 192, guiTop + 13 + 18 * i, true);
				if (stack != fillerStack) {
					if (mouseX >= guiLeft + 192 - 1 && mouseX <= guiLeft + 192 + 16 + 1) {
						if (mouseY >= guiTop + 13 + 18 * i - 1 && mouseY <= guiTop + 13 + 18 * i + 16 + 1) {
							getInstance().tooltipToDisplay =
								stack.getTooltip(
									Minecraft.getMinecraft().thePlayer,
									Minecraft.getMinecraft().gameSettings.advancedItemTooltips
								);
						}
					}
				}
			}
		}

		ItemStack[][][] inventories = getItemsForInventory(inventoryInfo, selectedInventory);
		if (currentInventoryIndex >= inventories.length) currentInventoryIndex = inventories.length - 1;
		if (currentInventoryIndex < 0) currentInventoryIndex = 0;

		ItemStack[][] inventory = inventories[currentInventoryIndex];
		if (inventory == null) {
			if (selectedInventory.equalsIgnoreCase("personal_vault_contents")) {
				Utils.drawStringCentered(
					EnumChatFormatting.RED + "Personal Vault API not enabled!",
					Minecraft.getMinecraft().fontRendererObj,
					guiLeft + 317,
					guiTop + 101,
					true,
					0
				);
			} else {
				Utils.drawStringCentered(
					EnumChatFormatting.RED + "Inventory API not enabled!",
					Minecraft.getMinecraft().fontRendererObj,
					guiLeft + 317,
					guiTop + 101,
					true,
					0
				);
			}
			return;
		}

		if (bestWeapons == null) {
			bestWeapons =
				findBestItems(
					inventoryInfo,
					6,
					new String[] { "inv_contents", "ender_chest_contents" },
					new String[] { "SWORD", "BOW" },
					DAMAGE_PATTERN,
					STRENGTH_PATTERN
				);
		}
		if (bestRods == null) {
			bestRods =
				findBestItems(
					inventoryInfo,
					3,
					new String[] { "inv_contents", "ender_chest_contents" },
					new String[] { "FISHING ROD" },
					FISHSPEED_PATTERN
				);
		}

		for (int i = 0; i < bestWeapons.length; i++) {
			if (bestWeapons[i] == null) continue;
			ItemStack stack = bestWeapons[i];
			Utils.drawItemStack(stack, guiLeft + 143, guiTop + 13 + 18 * i, true);
			if (mouseX >= guiLeft + 143 - 1 && mouseX <= guiLeft + 143 + 16 + 1) {
				if (mouseY >= guiTop + 13 + 18 * i - 1 && mouseY <= guiTop + 13 + 18 * i + 16 + 1) {
					getInstance().tooltipToDisplay = stack.getTooltip(Minecraft.getMinecraft().thePlayer, false);
				}
			}
		}

		for (int i = 0; i < bestRods.length; i++) {
			if (bestRods[i] == null) continue;
			ItemStack stack = bestRods[i];
			Utils.drawItemStack(stack, guiLeft + 143, guiTop + 137 + 18 * i, true);
			if (mouseX >= guiLeft + 143 - 1 && mouseX <= guiLeft + 143 + 16 + 1) {
				if (mouseY >= guiTop + 137 + 18 * i - 1 && mouseY <= guiTop + 137 + 18 * i + 16 + 1) {
					getInstance().tooltipToDisplay = stack.getTooltip(Minecraft.getMinecraft().thePlayer, false);
				}
			}
		}

		if (arrowCount == -1) {
			arrowCount = countItemsInInventory("ARROW", inventoryInfo, false, "quiver");
		}
		if (greenCandyCount == -1) {
			greenCandyCount = countItemsInInventory("GREEN_CANDY", inventoryInfo, true, "candy_inventory_contents");
		}
		if (purpleCandyCount == -1) {
			purpleCandyCount = countItemsInInventory("PURPLE_CANDY", inventoryInfo, true, "candy_inventory_contents");
		}

		Utils.drawItemStackWithText(
			NotEnoughUpdates.INSTANCE.manager.jsonToStack(NotEnoughUpdates.INSTANCE.manager.getItemInformation().get("ARROW")),
			guiLeft + 173,
			guiTop + 101,
			"" + (arrowCount > 999 ? GuiProfileViewer.shortNumberFormat(arrowCount, 0) : arrowCount),
			true
		);
		Utils.drawItemStackWithText(
			NotEnoughUpdates.INSTANCE.manager.jsonToStack(NotEnoughUpdates.INSTANCE.manager.getItemInformation().get("GREEN_CANDY")),
			guiLeft + 173,
			guiTop + 119,
			"" + greenCandyCount,
			true
		);
		Utils.drawItemStackWithText(
			NotEnoughUpdates.INSTANCE.manager.jsonToStack(NotEnoughUpdates.INSTANCE.manager.getItemInformation().get("PURPLE_CANDY")),
			guiLeft + 173,
			guiTop + 137,
			"" + purpleCandyCount,
			true
		);
		if (mouseX > guiLeft + 173 && mouseX < guiLeft + 173 + 16) {
			if (mouseY > guiTop + 101 && mouseY < guiTop + 137 + 16) {
				if (mouseY < guiTop + 101 + 17) {
					QuiverInfo quiverInfo = PlayerStats.getQuiverInfo(inventoryInfo, profile.getProfileInformation(profileId));
					if (quiverInfo == null) {
						getInstance().tooltipToDisplay = Utils.createList(EnumChatFormatting.RED + "Error checking Quiver");
					} else {
						getInstance().tooltipToDisplay = quiverInfo.generateProfileViewerTooltip();
					}
				} else if (mouseY < guiTop + 119 + 17) {
					getInstance().tooltipToDisplay =
						Utils.createList(EnumChatFormatting.GREEN + "Green Candy " + EnumChatFormatting.GRAY + "x" + greenCandyCount);
				} else {
					getInstance().tooltipToDisplay =
						Utils.createList(
							EnumChatFormatting.DARK_PURPLE + "Purple Candy " + EnumChatFormatting.GRAY + "x" + purpleCandyCount
						);
				}
			}
		}

		int inventoryRows = inventory.length;

		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(CHEST_GUI_TEXTURE);

		int invSizeY = inventoryRows * 18 + 17 + 7;

		int x = guiLeft + 320 - 176 / 2;
		int y = guiTop + 101 - invSizeY / 2;
		int staticSelectorHeight = guiTop + 177;

		getInstance().drawTexturedModalRect(x, y, 0, 0, 176, inventoryRows * 18 + 17);
		getInstance().drawTexturedModalRect(x, y + inventoryRows * 18 + 17, 0, 215, 176, 7);

		boolean leftHovered = false;
		boolean rightHovered = false;
		if (Mouse.isButtonDown(0)) {
			if (mouseY > staticSelectorHeight && mouseY < staticSelectorHeight + 16) {
				if (mouseX > guiLeft + 320 - 12 && mouseX < guiLeft + 320 + 12) {
					if (mouseX < guiLeft + 320) {
						leftHovered = true;
					} else {
						rightHovered = true;
					}
				}
			}
		}
		Minecraft.getMinecraft().getTextureManager().bindTexture(GuiProfileViewer.resource_packs);

		if (currentInventoryIndex > 0) {
			Utils.drawTexturedRect(
				guiLeft + 320 - 12,
				staticSelectorHeight,
				12,
				16,
				29 / 256f,
				53 / 256f,
				!leftHovered ? 0 : 32 / 256f,
				!leftHovered ? 32 / 256f : 64 / 256f,
				GL11.GL_NEAREST
			);
		}
		if (currentInventoryIndex < inventories.length - 1) {
			Utils.drawTexturedRect(
				guiLeft + 320,
				staticSelectorHeight,
				12,
				16,
				5 / 256f,
				29 / 256f,
				!rightHovered ? 0 : 32 / 256f,
				!rightHovered ? 32 / 256f : 64 / 256f,
				GL11.GL_NEAREST
			);
		}

		Minecraft
			.getMinecraft()
			.fontRendererObj.drawString(
				Utils.cleanColour(invNameToDisplayMap.get(selectedInventory).getDisplayName()),
				x + 8,
				y + 6,
				4210752
			);

		ItemStack stackToRender = null;
		int overlay = new Color(0, 0, 0, 100).getRGB();
		for (int yIndex = 0; yIndex < inventory.length; yIndex++) {
			if (inventory[yIndex] == null) continue;

			for (int xIndex = 0; xIndex < inventory[yIndex].length; xIndex++) {
				ItemStack stack = inventory[yIndex][xIndex];

				if (stack != null) Utils.drawItemStack(stack, x + 8 + xIndex * 18, y + 18 + yIndex * 18, true);

				if (
					getInstance().inventoryTextField.getText() != null &&
					!getInstance().inventoryTextField.getText().isEmpty() &&
					(
						stack == null ||
						!NotEnoughUpdates.INSTANCE.manager.doesStackMatchSearch(stack, getInstance().inventoryTextField.getText())
					)
				) {
					GlStateManager.translate(0, 0, 50);
					GuiScreen.drawRect(
						x + 8 + xIndex * 18,
						y + 18 + yIndex * 18,
						x + 8 + xIndex * 18 + 16,
						y + 18 + yIndex * 18 + 16,
						overlay
					);
					GlStateManager.translate(0, 0, -50);
				}

				if (stack == null || stack == fillerStack) continue;

				if (mouseX >= x + 8 + xIndex * 18 && mouseX <= x + 8 + xIndex * 18 + 16) {
					if (mouseY >= y + 18 + yIndex * 18 && mouseY <= y + 18 + yIndex * 18 + 16) {
						stackToRender = stack;
					}
				}
			}
		}
		if (stackToRender != null) {
			getInstance().tooltipToDisplay = stackToRender.getTooltip(Minecraft.getMinecraft().thePlayer, false);
		}
	}

	@Override
	public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		getInstance().inventoryTextField.setSize(88, 20);
		if (mouseX > guiLeft + 19 && mouseX < guiLeft + 19 + 88) {
			if (mouseY > guiTop + getInstance().sizeY - 26 - 20 && mouseY < guiTop + getInstance().sizeY - 26) {
				getInstance().inventoryTextField.mouseClicked(mouseX, mouseY, mouseButton);
				getInstance().playerNameTextField.otherComponentClick();
				return true;
			}
		}
		return false;
	}

	@Override
	public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
		int guiLeft = GuiProfileViewer.getGuiLeft();
		int guiTop = GuiProfileViewer.getGuiTop();

		if (mouseButton == 0) {
			int i = 0;
			for (Map.Entry<String, ItemStack> entry : invNameToDisplayMap.entrySet()) {
				int xIndex = i % 3;
				int yIndex = i / 3;

				int x = guiLeft + 19 + 34 * xIndex;
				int y = guiTop + 26 + 34 * yIndex;

				if (mouseX >= x && mouseX <= x + 16) {
					if (mouseY >= y && mouseY <= y + 16) {
						if (!selectedInventory.equals(entry.getKey())) Utils.playPressSound();
						selectedInventory = entry.getKey();
						return;
					}
				}

				i++;
			}

			JsonObject inventoryInfo = GuiProfileViewer.getProfile().getInventoryInfo(GuiProfileViewer.getProfileId());
			if (inventoryInfo == null) return;

			ItemStack[][][] inventories = getItemsForInventory(inventoryInfo, selectedInventory);
			if (currentInventoryIndex >= inventories.length) currentInventoryIndex = inventories.length - 1;
			if (currentInventoryIndex < 0) currentInventoryIndex = 0;

			ItemStack[][] inventory = inventories[currentInventoryIndex];
			if (inventory == null) return;

			int inventoryRows = inventory.length;

			int staticSelectorHeight = guiTop + 177;

			if (mouseY > staticSelectorHeight && mouseY < staticSelectorHeight + 16) {
				if (mouseX > guiLeft + 320 - 12 && mouseX < guiLeft + 320 + 12) {
					if (mouseX < guiLeft + 320) {
						currentInventoryIndex--;
					} else {
						currentInventoryIndex++;
					}
				}
			}
		}
	}

	@Override
	public void keyTyped(char typedChar, int keyCode) throws IOException {
		switch (keyCode) {
			case Keyboard.KEY_1:
			case Keyboard.KEY_NUMPAD1:
				selectedInventory = "inv_contents";
				break;
			case Keyboard.KEY_2:
			case Keyboard.KEY_NUMPAD2:
				selectedInventory = "ender_chest_contents";
				break;
			case Keyboard.KEY_3:
			case Keyboard.KEY_NUMPAD3:
				selectedInventory = "backpack_contents";
				break;
			case Keyboard.KEY_4:
			case Keyboard.KEY_NUMPAD4:
				selectedInventory = "personal_vault_contents";
				break;
			case Keyboard.KEY_5:
			case Keyboard.KEY_NUMPAD5:
				selectedInventory = "talisman_bag";
				break;
			case Keyboard.KEY_6:
			case Keyboard.KEY_NUMPAD6:
				selectedInventory = "wardrobe_contents";
				break;
			case Keyboard.KEY_7:
			case Keyboard.KEY_NUMPAD7:
				selectedInventory = "fishing_bag";
				break;
			case Keyboard.KEY_8:
			case Keyboard.KEY_NUMPAD8:
				selectedInventory = "potion_bag";
				break;
			default:
				getInstance().inventoryTextField.keyTyped(typedChar, keyCode);
				return;
		}
		Utils.playPressSound();
		getInstance().inventoryTextField.keyTyped(typedChar, keyCode);
	}

	@Override
	public void resetCache() {
		inventoryItems = new HashMap<>();
		bestWeapons = null;
		bestRods = null;
		armorItems = null;
		equipmentItems = null;
		currentInventoryIndex = 0;
		arrowCount = -1;
		greenCandyCount = -1;
		purpleCandyCount = -1;
	}

	private int countItemsInInventory(String internalname, JsonObject inventoryInfo, boolean specific, String... invsToSearch) {
		int count = 0;
		for (String inv : invsToSearch) {
			JsonArray invItems = inventoryInfo.get(inv).getAsJsonArray();
			for (int i = 0; i < invItems.size(); i++) {
				if (invItems.get(i) == null || !invItems.get(i).isJsonObject()) continue;
				JsonObject item = invItems.get(i).getAsJsonObject();
				if (
					(specific && item.get("internalname").getAsString().equals(internalname)) ||
					(!specific && item.get("internalname").getAsString().contains(internalname))
				) {
					if (item.has("count")) {
						count += item.get("count").getAsInt();
					} else {
						count += 1;
					}
				}
			}
		}
		return count;
	}

	private ItemStack[] findBestItems(
		JsonObject inventoryInfo,
		int numItems,
		String[] invsToSearch,
		String[] typeMatches,
		Pattern... importantPatterns
	) {
		ItemStack[] bestItems = new ItemStack[numItems];
		TreeMap<Integer, Set<ItemStack>> map = new TreeMap<>();
		for (String inv : invsToSearch) {
			JsonArray invItems = inventoryInfo.get(inv).getAsJsonArray();
			for (int i = 0; i < invItems.size(); i++) {
				if (invItems.get(i) == null || !invItems.get(i).isJsonObject()) continue;
				JsonObject item = invItems.get(i).getAsJsonObject();
				JsonArray lore = item.get("lore").getAsJsonArray();
				if (Utils.checkItemType(lore, true, typeMatches) >= 0) {
					int importance = 0;
					for (int j = 0; j < lore.size(); j++) {
						String line = lore.get(j).getAsString();
						for (Pattern pattern : importantPatterns) {
							Matcher matcher = pattern.matcher(Utils.cleanColour(line));
							if (matcher.find()) {
								importance += Integer.parseInt(matcher.group(1));
							}
						}
					}
					map.computeIfAbsent(importance, k -> new HashSet<>()).add(NotEnoughUpdates.INSTANCE.manager.jsonToStack(item, false));
				}
			}
		}
		int i = 0;
		outer:for (int key : map.descendingKeySet()) {
			Set<ItemStack> items = map.get(key);
			for (ItemStack item : items) {
				bestItems[i] = item;
				if (++i >= bestItems.length) break outer;
			}
		}

		return bestItems;
	}

	private ItemStack[][][] getItemsForInventory(JsonObject inventoryInfo, String invName) {
		if (inventoryItems.containsKey(invName)) return inventoryItems.get(invName);

		JsonArray jsonInv = Utils.getElement(inventoryInfo, invName).getAsJsonArray();

		if (jsonInv.size() == 0) return new ItemStack[1][][];

		int jsonInvSize;
		if (useActualMax(invName)) {
			jsonInvSize = (int) Math.ceil(jsonInv.size() / 9f) * 9;
		} else {
			jsonInvSize = 9 * 4;
			float divideBy = 9f;
			if (invName.equals("wardrobe_contents")) {
				divideBy = 36f;
			}
			for (int i = 9 * 4; i < jsonInv.size(); i++) {
				JsonElement item = jsonInv.get(i);
				if (item != null && item.isJsonObject()) {
					jsonInvSize = (int) (Math.ceil((i + 1) / divideBy) * (int) divideBy);
				}
			}
		}

		int rowSize = 9;
		int rows = jsonInvSize / rowSize;
		int maxRowsPerPage = getRowsForInventory(invName);
		int maxInvSize = rowSize * maxRowsPerPage;

		int numInventories = (jsonInvSize - 1) / maxInvSize + 1;
		JsonArray backPackSizes = (JsonArray) inventoryInfo.get("backpack_sizes");
		if (invName.equals("backpack_contents")) {
			numInventories = backPackSizes.size();
		}

		ItemStack[][][] inventories = new ItemStack[numInventories][][];

		//int availableSlots = getAvailableSlotsForInventory(inventoryInfo, collectionInfo, invName);
		int startNumberJ = 0;

		for (int i = 0; i < numInventories; i++) {
			int thisRows = Math.min(maxRowsPerPage, rows - maxRowsPerPage * i);
			int invSize;

			if (invName.equals("backpack_contents")) {
				thisRows = backPackSizes.get(i).getAsInt() / 9;
				invSize = startNumberJ + (thisRows * 9);
				maxInvSize = thisRows * 9;
			} else {
				startNumberJ = maxInvSize * i;
				invSize = Math.min(jsonInvSize, maxInvSize + maxInvSize * i);
			}
			if (thisRows <= 0) break;

			ItemStack[][] items = new ItemStack[thisRows][rowSize];

			for (int j = startNumberJ; j < invSize; j++) {
				int xIndex = (j % maxInvSize) % rowSize;
				int yIndex = (j % maxInvSize) / rowSize;
				if (invName.equals("inv_contents")) {
					yIndex--;
					if (yIndex < 0) yIndex = rows - 1;
				}
				if (yIndex >= thisRows) {
					break;
				}

				if (j >= jsonInv.size()) {
					items[yIndex][xIndex] = fillerStack;
					continue;
				}
				if (jsonInv.get(j) == null || !jsonInv.get(j).isJsonObject()) {
					continue;
				}

				JsonObject item = jsonInv.get(j).getAsJsonObject();
				ItemStack stack = NotEnoughUpdates.INSTANCE.manager.jsonToStack(item, false);
				if (item.has("item_contents")) {
					JsonArray bytesArr = item.get("item_contents").getAsJsonArray();
					byte[] bytes = new byte[bytesArr.size()];
					for (int bytesArrI = 0; bytesArrI < bytesArr.size(); bytesArrI++) {
						bytes[bytesArrI] = bytesArr.get(bytesArrI).getAsByte();
					}
					//byte[] bytes2 = null;
					NBTTagCompound tag = stack.getTagCompound();
					if (tag != null && tag.hasKey("ExtraAttributes", 10)) {
						NBTTagCompound ea = tag.getCompoundTag("ExtraAttributes");
						for (String key : ea.getKeySet()) {
							if (key.endsWith("backpack_data") || key.equals("new_year_cake_bag_data")) {
								ea.setTag(key, new NBTTagByteArray(bytes));
								break;
							}
						}
						tag.setTag("ExtraAttributes", ea);
						stack.setTagCompound(tag);
					}
				}

				items[yIndex][xIndex] = stack;
			}
			inventories[i] = items;
			if (invName.equals("backpack_contents")) {
				startNumberJ = startNumberJ + backPackSizes.get(i).getAsInt();
			}
		}

		inventoryItems.put(invName, inventories);
		return inventories;
	}

	private boolean useActualMax(String invName) {
		switch (invName) {
			case "talisman_bag":
			case "fishing_bag":
			case "potion_bag":
			case "personal_vault_contents":
				return true;
		}
		return false;
	}

	private int getRowsForInventory(String invName) {
		switch (invName) {
			case "wardrobe_contents":
				return 4;
			case "backpack_contents":
			case "ender_chest_contents":
				return 5;
			default:
				return 6;
		}
	}
}
