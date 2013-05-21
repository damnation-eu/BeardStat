package com.tehbeard.BeardStat.commands.formatters;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.tehbeard.BeardStat.containers.IStat;

public class FormatFactory {

    public static final FormatFactory  instance = new FormatFactory();
    private Map<String, StatFormatter> mapping  = new HashMap<String, StatFormatter>();

    private FormatFactory() {
        this.mapping.put("stats.playedfor", new StatFormatter() {

            @Override
            public String format(int value) {
                long seconds = value;
                int weeks = (int) seconds / 604800;
                int days = (int) Math.ceil((seconds - (604800 * weeks)) / 86400);
                int hours = (int) Math.ceil((seconds - ((86400 * days) + (604800 * weeks))) / 3600);
                int minutes = (int) Math.ceil((seconds - ((604800 * weeks) + (86400 * days) + (3600 * hours))) / 60);

                return weeks + "weeks " + days + "days " + hours + "hours " + minutes + "mins";
            }
        });

        StatFormatter date = new StatFormatter() {

            @Override
            public String format(int value) {
                return (new Date(value)).toString();
            }

        };
        this.mapping.put("stats.firstlogin", date);
        this.mapping.put("stats.lastlogin", date);
        this.mapping.put("stats.lastlogout", date);
    }

    public static String formatStat(IStat stat) {
        String tag = stat.getCategory() + "." + stat.getStatistic();
        StatFormatter f = instance.mapping.get(tag);
        if (f != null) {
            return f.format(stat.getValue());
        }
        return "" + stat.getValue();
    }

    public static void addStringFormat(String cat, String stat, String format) {
        String tag = cat + "." + stat;

        instance.mapping.put(tag, new StaticStatFormatter(format));

    }

}
