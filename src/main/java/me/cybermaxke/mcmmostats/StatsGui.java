/**
 * 
 * This software is part of the mcMMOStatsGui
 * 
 * mcMMOStatsGui is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or 
 * any later version.
 *  
 * mcMMOStatsGui is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with mcMMOStatsGui. If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package me.cybermaxke.mcmmostats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.server.v1_5_R1.IObjective;
import net.minecraft.server.v1_5_R1.Packet206SetScoreboardObjective;
import net.minecraft.server.v1_5_R1.Packet207SetScoreboardScore;
import net.minecraft.server.v1_5_R1.Packet208SetScoreboardDisplayObjective;
import net.minecraft.server.v1_5_R1.Scoreboard;
import net.minecraft.server.v1_5_R1.ScoreboardObjective;
import net.minecraft.server.v1_5_R1.ScoreboardScore;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.player.UserManager;

public class StatsGui {
	private Scoreboard scoreboard;
	private ScoreboardObjective skillStats;

	private int i;
	private int ticksBeforeReturn = 200;
	private Updater updater;

	private Map<ScoreboardObjective, List<ScoreboardScore>> last = new HashMap<ScoreboardObjective, List<ScoreboardScore>>();
	private Map<SkillType, ScoreboardObjective> skills = new HashMap<SkillType, ScoreboardObjective>();

	private Player player;
	private McMMOPlayer mcplayer;

	public StatsGui(Player player) {
		this.scoreboard = new Scoreboard();
		this.player = player;
		this.mcplayer = UserManager.getPlayer(player);
		this.skillStats = this.scoreboard.a("McMMOSkillStats", IObjective.b);
		this.skillStats.a("Skill Stats");

		for (SkillType t : SkillType.values()) {
			if (McMMOStats.getSkillName(t) != null) {
				ScoreboardObjective obj = this.scoreboard.a("Sk" + t.toString(), IObjective.b);
				obj.a(McMMOStats.getSkillName(t));
				this.skills.put(t, obj);
			}
		}

		this.updater = new Updater(this);
		this.updater.runTaskTimer(McMMOStats.getInstance(), 0, 1);
	}

	public void onTick() {
		++this.i;
		if (this.i > this.ticksBeforeReturn) {
			this.i = 0;
			this.sendAllStats();
		}
	}

	public void sendSkillStats(SkillType type) {
		this.i = 0;
		Map<String, Integer> m = new HashMap<String, Integer>();
		PlayerProfile pr = this.mcplayer.getProfile();

		m.put("Level", pr.getSkillLevel(type));
		m.put("Required XP", pr.getXpToLevel(type));
		m.put("Earned XP", pr.getSkillXpLevel(type));
		this.sendScores(this.skills.get(type), m);
	}

	public void sendAllStats() {
		Map<String, Integer> m = new HashMap<String, Integer>();
		m.put("Power Level", this.mcplayer.getPowerLevel());

		for (SkillType t : SkillType.values()) {
			if (McMMOStats.getSkillName(t) != null) {
				m.put(McMMOStats.getSkillName(t), this.mcplayer.getProfile().getSkillLevel(t));
			}
		}

		this.sendScores(this.skillStats, m);
	}

	public void sendScores(ScoreboardObjective objective, Map<String, Integer> values) {
		List<ScoreboardScore> scores = new ArrayList<ScoreboardScore>();

		for (Entry<String, Integer> en : values.entrySet()) {
			ScoreboardScore s = this.scoreboard.a(en.getKey(), objective);
			s.c(en.getValue());
			scores.add(s);
		}

		if (this.last.containsKey(objective)) {
			for (ScoreboardScore s : this.last.get(objective)) {
				PacketUtils.sendPacket(this.player, this.getRemoveScorePacket(s));
			}
		}

		for (ScoreboardScore s : scores) {
			PacketUtils.sendPacket(this.player, this.getCreateScorePacket(s));
		}

		PacketUtils.sendPacket(this.player, this.getDisplayPacket(1, objective));
		this.last.put(objective, scores);
	}

	public void show() {
		PacketUtils.sendPacket(this.player, this.getCreatePacket(this.skillStats));
		for (SkillType t : SkillType.values()) {
			if (this.skills.containsKey(t)) {
				PacketUtils.sendPacket(this.player, this.getCreatePacket(this.skills.get(t)));
			}
		}
		PacketUtils.sendPacket(this.player, this.getDisplayPacket(1, this.skillStats));
		this.sendAllStats();
	}

	public void hide() {
		PacketUtils.sendPacket(this.player, this.getRemovePacket(this.skillStats));
		for (SkillType t : SkillType.values()) {
			if (this.skills.containsKey(t)) {
				PacketUtils.sendPacket(this.player, this.getRemovePacket(this.skills.get(t)));
			}
		}
	}

	public void removeUpdater() {
		this.updater.cancel();
	}

	private Packet208SetScoreboardDisplayObjective getDisplayPacket(int slot, ScoreboardObjective objective) {
		return new Packet208SetScoreboardDisplayObjective(slot, objective);
	}

	private Packet206SetScoreboardObjective getCreatePacket(ScoreboardObjective objective) {
		return new Packet206SetScoreboardObjective(objective, 0);
	}

	private Packet206SetScoreboardObjective getRemovePacket(ScoreboardObjective objective) {
		return new Packet206SetScoreboardObjective(objective, 1);
	}

	private Packet207SetScoreboardScore getCreateScorePacket(ScoreboardScore score) {
		return new Packet207SetScoreboardScore(score, 0);
	}

	private Packet207SetScoreboardScore getRemoveScorePacket(ScoreboardScore score) {
		return new Packet207SetScoreboardScore(score, 1);
	}

	private class Updater extends BukkitRunnable {
		private StatsGui gui;

		public Updater(StatsGui gui) {
			this.gui = gui;
		}

		@Override
		public void run() {
			this.gui.onTick();
		}
	}
}