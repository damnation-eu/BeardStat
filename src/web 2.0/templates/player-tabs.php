<?php 
$p = new SPlayer($_GET['playerName']);
$tabs = new StatTabs("tabs.json");
?>
   <div style="float:left"><h2><canvas class="head head-huge" data-name="<?php echo $p->name;?>"></canvas><?php echo $p->name; ?></h2></div>
	
	<div style="float:right;margin-top:20px;margin-right:20px;margin-bottom:0px;">
	<form action="showplayer.php" method="get">
		<div class="input-append">
			<input type="hidden" name="search"> <input type="text"
				name="playerName" placeholder="player name">
			<button class="btn" type="submit">Search!</button>
		</div>
	</form>
	</div>
	
	<div class="tab-stats span7">
		<ul class="nav nav-tabs">
			<?php 
			//Print headings
			$firstTab = true;
			while($tabs->have_tabs()){
 $id = $tabs->the_tab_id();
 $name = $tabs->the_tab_name();
 ?>
			<li class="<?php echo ($firstTab ? "active":"") ?>"><a
				href="#<?php echo $id;?>" data-toggle="tab"><?php echo $name;?></a></li>
			<?php
			$firstTab = false;
}
?></ul><div class="tab-content"><?php 
$tabs->reset_tabs();
$dump = "";
$firstTab = true;
while($tabs->have_tabs()){
  $id = $tabs->the_tab_id();
  $name = $tabs->the_tab_name();
  
  $dump .= "Making tab $id:$name\n";//DUMP
  
  
  echo "<div id=\"$id\" class=\"tab-pane fade " . ($firstTab ? "active in":"") . "\">";
  if($firstTab){
$firstTab=false;
}

echo "<table class=\"table table-bordered\">";
while($tabs->have_headings()){
  
  echo"<tr><td colspan=\"2\"><h3>" . $tabs->the_heading_name() ."</h3></td></tr>";
  $dump .= "  Making heading " . $tabs->the_heading_name() . "\n";//DUMP
  while($tabs->have_entries()){
   $dump .= "    Making entry " . $tabs->the_entry_label() . "\n";//DUMP
   echo "<tr><td>" . $tabs->the_entry_label() . "</td><td>" . $tabs->the_entry_value_for_player($p)->getValueFormatted() . "</td></tr>";
  }
  $tabs->reset_entries();

 }
 $tabs->reset_headings();
 echo "</table></div>";
}
$tabs->reset_tabs();
?>
</div>
</div>