package red.man10.servercopy_115

import com.sk89q.worldedit.bukkit.WorldEditPlugin
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.Region
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.sql.ResultSet
import java.util.concurrent.ExecutionException
import org.bukkit.scheduler.BukkitRunnable
import red.man10.man10slot.MySQLManager
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import kotlin.collections.HashMap

class ServerCopy_115 : JavaPlugin(){

    val prefix = "§l[§b§lServer§e§lCopy§f§l]"
    val l = Bukkit.getLogger()

    var blockMap = HashMap<String, BlockData>()

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()

        getCommand("scopy")!!.setExecutor(this)

        val c = MysqlTableCreate(this)
        c.start()

        val m = LoadBlockData(this, this.server.consoleSender)
        m.start()

    }

    override fun onDisable() {
        // Plugin shutdown logic

        val m = SaveBlockData(this, this.server.consoleSender)
        m.start()

    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return false

        val p: Player = sender

        if (!p.hasPermission("scopy.use"))return false

        when(args.size){

            0->{
                sender.sendMessage("§f§l--------${prefix}§f§l--------")
                sender.sendMessage("§6§l/scopy copy [名前]§f§r:WEで選択してる範囲を保存する")
                sender.sendMessage("§6§l/scopy paste [登録したuser名:名前]§f§r:そのuserが登録した[名前]の建造物を張り付ける")
                sender.sendMessage("§6§l/scopy save §f§r:登録したものをDBに保存します。")
                sender.sendMessage("§6§l/scopy list [登録したuser名]§f§r:[user]が登録した名前の一覧が見れる")
                sender.sendMessage("§6§l/scopy remove [登録したuser名:名前]§f§r:そのuserが登録した[名前]の建造物を消去する")
                sender.sendMessage("§bcreated by Ryotackey")
                sender.sendMessage("§l--------------------------------------------")

                return true
            }

            1->{

                if (args[0].equals("save", ignoreCase = true)){

                    val m = SaveBlockData(this, sender)
                    m.start()

                    return true

                }

                if(args[0].equals("reload", ignoreCase = true)){

                    reloadConfig()
                    val m = MySQLManager(this, "ServerCopy")
                    m.loadConfig()

                    p.sendMessage("§aReload Complete")

                    return true

                }

            }

            2->{

                if (args[0].equals("list", ignoreCase = true)){

                    if (!p.hasPermission("scopy.use")){
                        p.sendMessage("§c権限がありません")
                        return true
                    }

                    p.sendMessage("§a登録名一覧")

                    p.sendMessage("§e§l${args[1]}")

                    for (i in blockMap) {

                        val user = i.key.split(":")[0]

                        if (user != args[1]) continue

                        val s = i.key.split(":")[1]
                        p.sendMessage("§f$s")

                    }

                    return true
                }

                if (args[0].equals("copy", ignoreCase = true)) {

                    if (!p.hasPermission("scopy.use")){
                        p.sendMessage("§c権限がありません")
                        return true
                    }

                    if (blockMap.containsKey("${p.name}:${args[1]}")){
                        p.sendMessage("§c既に登録されています")
                        return true
                    }

                    val we = getWorldEdit()

                    if (we == null) {
                        p.sendMessage("§cWorldEditが入ってません")
                        return true
                    }

                    val sel: Region?

                    try {
                        sel = we.getSession(p).getSelection(we.getSession(p).selectionWorld)
                    } catch (e: NullPointerException){
                        p.sendMessage("§c先にWorldEditで選択する必要があります");
                        return true
                    }


                    val min = sel.minimumPoint
                    val max = sel.maximumPoint

                    val startX = Math.min(min.getBlockX(), max.getBlockX())
                    val endX = Math.max(min.getBlockX(), max.getBlockX())

                    val startY = Math.min(min.getBlockY(), max.getBlockY())
                    val endY = Math.max(min.getBlockY(), max.getBlockY())

                    val startZ = Math.min(min.getBlockZ(), max.getBlockZ())
                    val endZ = Math.max(min.getBlockZ(), max.getBlockZ())

                    val blocks = mutableListOf<String>()
                    val datas = BlockData()

                    val w = p.world

                    for (x in startX..endX) {
                        for (y in startY..endY) {
                            for (z in startZ..endZ) {

                                val loc = Location(w, x.toDouble(), y.toDouble(), z.toDouble())

                                try {
                                    val block = loc.block
                                    blocks.add(block.blockData.material.toString())
                                } catch (a: IllegalStateException) {

                                    p.sendMessage("§c選択範囲のチャンクがすべて読み込まれていません")
                                    return false

                                }
                            }
                        }
                    }

                    datas.mate = blocks
                    datas.startloc = Location(w, startX.toDouble(), startY.toDouble(), startZ.toDouble())
                    datas.endloc = Location(w, endX.toDouble(), endY.toDouble(), endZ.toDouble())

                    blockMap["${p.name}:${args[1]}"] = datas

                    p.sendMessage("§a完了しました")

                    return true

                }

                if (args[0].equals("paste", ignoreCase = true)){

                    if (!p.hasPermission("scopy.use")){
                        p.sendMessage("§c権限がありません")
                        return true
                    }

                    if (!args[1].contains(":")){
                        p.sendMessage("§c使い方が違います")
                        return true
                    }

                    val list = args[1].split(":")

                    val key = "${list[0]}:${list[1]}"

                    if (!blockMap.containsKey(key)){
                        p.sendMessage("§c登録されていません")
                        return true
                    }
                    val blockdata = blockMap[key]!!

                    val startX = blockdata.startloc!!.blockX
                    val endX = blockdata.endloc!!.blockX

                    val startY = blockdata.startloc!!.blockY
                    val endY = blockdata.endloc!!.blockY

                    val startZ = blockdata.startloc!!.blockZ
                    val endZ = blockdata.endloc!!.blockZ

                    val min = p.location

                    val x1 = endX - startX
                    val y1 = endY - startY
                    val z1 = endZ - startZ

                    val x2 = min.x + x1
                    val y2 = min.y + y1
                    val z2 = min.z + z1

                    val w = p.world

                    val max = Location(w, x2, y2, z2)



                    val loclist = mutableListOf<Location>()

                    for (x in min.blockX..max.blockX) {
                        for (y in min.blockY..max.blockY) {
                            for (z in min.blockZ..max.blockZ) {

                                val loc = Location(w, x.toDouble(), y.toDouble(), z.toDouble())

                                loclist.add(loc)

                            }
                        }
                    }

                    blockPlace(blockdata, p, loclist)

                    return true

                }

                if (args[0].equals("remove", ignoreCase = true)){

                    if (!p.hasPermission("scopy.use")){
                        p.sendMessage("§c権限がありません")
                        return true
                    }

                    if (!args[1].contains(":")){
                        p.sendMessage("§c使い方が違います")
                        return true
                    }

                    val list = args[1].split(":")

                    blockMap.remove(args[1])

                    val m = RemoveBlockData(this, p, list[1], list[0])
                    m.start()

                    return true

                }

            }

        }
        return false
    }

    fun getWorldEdit(): WorldEditPlugin? {

        val p = Bukkit.getServer().pluginManager.getPlugin("WorldEdit")

        if (p is WorldEditPlugin)return p
        else return null

    }

    fun blockPlace(bd: BlockData, p: Player, loc: MutableList<Location>){

        var count = 0

        val material = bd.mate

        object : BukkitRunnable() {

            val amount = 4000

            var per = 0.0

            @Synchronized
            override fun run() {

                var count2 = 0

                for (i in 0 until loc.size){

                    if (count > count2) {
                        count2++
                        continue
                    }

                    val world = loc[i].world

                    val chunk = world!!.getChunkAt(loc[i])

                    if (!chunk.isLoaded)chunk.load()

                    val b = world.getBlockAt(loc[i])

                    b.type = Material.getMaterial(material[i])!!

                    count++

                    if (Math.floor(i/loc.size.toDouble()*100) != per){
                        per = Math.floor(i/loc.size.toDouble()*100)
                        p.sendMessage("§e現在:§f$per%")
                    }

                    if (count == loc.size-1){
                        cancel()
                        p.sendMessage("§a完了しました")
                        return
                    }

                    if (count % amount ==0)break

                    count2++
                }

            }
        }.runTaskTimer(this, 0, 50)
    }

    class BlockData{

        var mate = mutableListOf<String>()

        var startloc: Location? = null
        var endloc: Location? = null

    }

}

class RemoveBlockData(val plugin: ServerCopy_115, val p: Player, val title: String, val username: String): Thread(){

    @Synchronized
    override fun run() {

        val mysql = MySQLManager(plugin, "ServerCopy")

        val result = mysql.execute("DELETE FROM servercopytable WHERE name='$title' AND username='$username';")

        mysql.close()

        if (result){
            p.sendMessage("§a削除しました")
        }else{
            p.sendMessage("§c削除に失敗しました")
        }

    }

}

class MysqlTableCreate(val plugin : ServerCopy_115): Thread(){

    override fun run() {

        val mysql = MySQLManager(plugin, "ServerCopy")

        mysql.execute("CREATE TABLE IF NOT EXISTS `servercopytable` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `name` varchar(45) DEFAULT NULL,\n" +
                "  `material` longtext,\n" +
                "  `username` varchar(45) DEFAULT NULL,\n" +
                "  `world` varchar(45) DEFAULT NULL,\n" +
                "  `startx` int(11) DEFAULT NULL,\n" +
                "  `starty` int(11) DEFAULT NULL,\n" +
                "  `startz` int(11) DEFAULT NULL,\n" +
                "  `endx` int(11) DEFAULT NULL,\n" +
                "  `endy` int(11) DEFAULT NULL,\n" +
                "  `endz` int(11) DEFAULT NULL,\n" +
                "  `save_date` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8;")

    }

}

class SaveBlockData(val plugin: ServerCopy_115, val sender: CommandSender): Thread(){

    @Synchronized
    override fun run() {

        val mysql = MySQLManager(plugin, "ServerCopy")

        for (i in plugin.blockMap) {

            val key = i.key.split(":")

            var rs: ResultSet? = null

            try {
                rs = mysql.query("SELECT count(1) FROM servercopytable WHERE name='${key[1]}' AND username='${key[0]}';")
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }

            if (rs == null) continue

            rs.first()

            if (rs.getInt("count(1)") != 0) continue

            mysql.close()

            val list = devide(i.value.mate, 10000)

            val bd = mutableListOf<String>()

            for (j in list) {

                val matestr = StringBuilder()
                for (h in 0 until j.size) {
                    if (h != j.size-1)matestr.append("${j[h]}:")
                    else matestr.append(j[h])
                }

                bd.add(matestr.toString())

            }

            for (j in 0 until bd.size) {

                val b = mysql.execute("INSERT INTO test.servercopytable (name, material, username, world, startx, starty, startz, endx, endy, endz) VALUES ('${key[1]}', '${bd[j]}', '${key[0]}', '${i.value.startloc!!.world!!.name}', " +
                        "'${i.value.startloc!!.blockX}', '${i.value.startloc!!.blockY}', '${i.value.startloc!!.blockZ}', '${i.value.endloc!!.blockX}', '${i.value.endloc!!.blockY}', '${i.value.endloc!!.blockZ}');")
                if (b) {
                    sender.sendMessage("§e${j + 1}/${bd.size}§fsave done")
                } else {
                    sender.sendMessage("§c保存に失敗しました")
                    return
                }

            }
            sender.sendMessage("§a保存が完了しました")

        }

    }

    fun  devide(origin: MutableList<String>?, size: Int): List<List<String>> {
        if (origin == null || origin.isEmpty() || size <= 0) {
            return Collections.emptyList()
        }

        val block = origin.size / size + if (origin.size % size > 0) 1 else 0

        val devidedList = mutableListOf<MutableList<String>>()
        for (i in 0 until block) {
            val start = i * size
            val end = Math.min(start + size, origin.size)
            devidedList.add(origin.subList(start, end))
        }

        return devidedList
    }

}

class LoadBlockData(val plugin: ServerCopy_115, val sender: CommandSender): Thread(){

    @Synchronized
    override fun run() {

        val mysql = MySQLManager(plugin, "ServerCopy")

        var rs: ResultSet? = null

        try {
            rs = mysql.query("SELECT * FROM servercopytable;")
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }

        if (rs == null) {
            sender.sendMessage("§cテーブル情報が読み込めないか、データが存在しないため失敗しました")
            mysql.close()
            return
        }

        val map = HashMap<String, ServerCopy_115.BlockData>()

        while (rs.next()) {

            val key = rs.getString("username") + ":" + rs.getString("name")

            if (map.containsKey(key)) {

                val bd = map[key]!!.mate

                val matelist = rs.getString("material").split(":")

                for (i in matelist) {
                    bd.add(i)
                }

                map[key]!!.mate = bd

            }else {

                val bd = ServerCopy_115.BlockData()
                bd.mate = rs.getString("material").split(":") as MutableList<String>

                val startloc = Location(Bukkit.getWorld(rs.getString("world")), rs.getInt("startx").toDouble(), rs.getInt("starty").toDouble(), rs.getInt("startz").toDouble())
                val endloc = Location(Bukkit.getWorld(rs.getString("world")), rs.getInt("endx").toDouble(), rs.getInt("endy").toDouble(), rs.getInt("endz").toDouble())

                bd.startloc = startloc
                bd.endloc = endloc

                map[key] = bd

            }

        }

        plugin.blockMap = map

        rs.close()
        mysql.close()

    }

}