package li.cil.oc.common

import java.io
import java.util.Random

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.util.Color
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.WeightedRandomChestContent
import net.minecraftforge.common.ChestGenHooks
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

import scala.collection.convert.WrapAsScala._
import scala.collection.mutable

object Loot {
  val builtInDisks = mutable.Map.empty[String, (ItemStack, Int)]

  val worldDisks = mutable.Map.empty[String, (ItemStack, Int)]

  val disks = mutable.ArrayBuffer.empty[ItemStack]

  def randomDisk(rng: Random) =
    if (disks.length > 0) Some(disks(rng.nextInt(disks.length)))
    else None

  def init() {
    val list = new java.util.Properties()
    val listStream = getClass.getResourceAsStream("/assets/" + Settings.resourceDomain + "/loot/loot.properties")
    list.load(listStream)
    listStream.close()
    parseLootDisks(list, builtInDisks)

    val loot = new Loot(createLootDisk("openos", "OpenOS", Some("dyeGreen")))
    val containers = Array(
      ChestGenHooks.DUNGEON_CHEST,
      ChestGenHooks.PYRAMID_DESERT_CHEST,
      ChestGenHooks.PYRAMID_JUNGLE_CHEST,
      ChestGenHooks.STRONGHOLD_LIBRARY)
    for (container <- containers) {
      ChestGenHooks.addItem(container, loot)
    }
  }

  @SubscribeEvent
  def initForWorld(e: WorldEvent.Load) {
    worldDisks.clear()
    disks.clear()
    if (!e.world.isRemote) {
      val path = new io.File(DimensionManager.getCurrentSaveRootDirectory, Settings.savePath + "loot/")
      if (path.exists && path.isDirectory) {
        val listFile = new io.File(path, "loot.properties")
        if (listFile.exists && listFile.isFile) {
          try {
            val listStream = new io.FileInputStream(listFile)
            val list = new java.util.Properties()
            list.load(listStream)
            listStream.close()
            parseLootDisks(list, worldDisks)
          }
          catch {
            case t: Throwable => OpenComputers.log.warn("Failed opening loot descriptor file in saves folder.")
          }
        }
      }
    }
    for ((name, entry) <- builtInDisks if !worldDisks.contains(name)) {
      worldDisks += name -> entry
    }
    for ((_, (stack, count)) <- worldDisks) {
      for (i <- 0 until count) {
        disks += stack
      }
    }
  }

  private def parseLootDisks(list: java.util.Properties, acc: mutable.Map[String, (ItemStack, Int)]) {
    for (key <- list.stringPropertyNames) {
      val value = list.getProperty(key)
      try value.split(":") match {
        case Array(name, count, color) =>
          acc += key -> ((createLootDisk(name, key, Some(color)), count.toInt))
        case Array(name, count) =>
          acc += key -> ((createLootDisk(name, key), count.toInt))
        case _ =>
          acc += key -> ((createLootDisk(value, key), 1))
      }
      catch {
        case t: Throwable => OpenComputers.log.warn("Bad loot descriptor: " + value, t)
      }
    }
  }

  def createLootDisk(name: String, path: String, color: Option[String] = None) = {
    val data = new NBTTagCompound()
    data.setString(Settings.namespace + "fs.label", name)

    val tag = new NBTTagCompound()
    tag.setTag(Settings.namespace + "data", data)
    // Store this top level, so it won't get wiped on save.
    tag.setString(Settings.namespace + "lootPath", path)
    color match {
      case Some(oreDictName) =>
        tag.setInteger(Settings.namespace + "color", Color.dyes.indexOf(oreDictName))
      case _ =>
    }

    val disk = api.Items.get(Constants.ItemName.Floppy).createItemStack(1)
    disk.setTagCompound(tag)

    disk
  }
}

class Loot(baseItem: ItemStack) extends WeightedRandomChestContent(baseItem, 1, 1, Settings.get.lootProbability) {
  override def generateChestContent(random: Random, newInventory: IInventory) =
    Loot.randomDisk(random) match {
      case Some(disk) =>
        ChestGenHooks.generateStacks(random, disk,
          theMinimumChanceToGenerateItem, theMaximumChanceToGenerateItem)
      case _ => Array.empty
    }
}
