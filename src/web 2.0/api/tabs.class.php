<?php

Class StatTabs{
 
 private $data;
 
 private $tabIdx = -1;
 private $headingIdx = -1;
 private $entryIdx = -1;
 
 
 public static $statLookup = null;
 
 /**
  * Bootstrap lookup table
  */
 static function init(){
  StatTabs::$statLookup = getLookup("statistic","statistic");
 }
 
 /**
  * Load a tabs definition from a file
  * @param unknown $file
  */
 function __construct($file){
  $this->data = json_decode(file_get_contents(BEARDSTAT_API_DIR . "..\\config\\" . $file));
 }
 
 function reset_tabs(){
  $this->tabIdx = -1;
 }
 
 function reset_headings(){
  $this->headingIdx = -1;
 }
 
 function reset_entries(){
  $this->entryIdx = -1;
 }
 
 function reset_all(){
  $this->reset_tabs();
  $this->reset_headings();
  $this->reset_entries();
 }
 /**
  * Main loop, increments the tab index and checks if it is less than 
  */
 function have_tabs(){
   $this->tabIdx ++;
   if($this->tabIdx < count($this->data)){
    //start of loop, reset the counters below us
    $this->reset_headings();
    $this->reset_entries();
    
    return true;    
   }
   else
   {
    return false;
   }
 }
 
 function the_tab_name(){
   return $this->data[$this->tabIdx]->tabName;
 }
 
 function the_tab_id(){
  return str_ireplace(" ", "-", $this->the_tab_name());
 }
 
 function have_headings(){
  $this->headingIdx ++;
  if($this->headingIdx < count($this->data[$this->tabIdx])){
   $this->reset_entries();
   return true;
  }
  else
  {
   return false;
  }
 }
 
 function the_heading_name(){
  return $this->data[$this->tabIdx]->headings[$this->headingIdx]->headingName;
 }
 
 function the_heading_id(){
  return str_ireplace(" ", "-", $this->the_heading_name());
 }
 
 function have_entries(){
  $this->entryIdx ++;
  return ($this->entryIdx < count($this->data[$this->tabIdx]->headings[$this->headingIdx]->display));
 }
 
 function the_entry(){
  return $this->data[$this->tabIdx]->headings[$this->headingIdx]->display[$this->entryIdx];
 }
 
 function the_entry_value_for_player($player){
  $entry = $this->the_entry();
  
  return $player->getStat($entry->domain,$entry->world,$entry->cat,$entry->stat);
 }
 
 function the_entry_label(){

  return isset(StatTabs::$statLookup[$this->the_entry()->stat]) ? (strlen(StatTabs::$statLookup[$this->the_entry()->stat]["name"]) > 0 ? StatTabs::$statLookup[$this->the_entry()->stat]["name"] : $this->the_entry()->stat) : '[[' . $this->the_entry()->stat . ']]'; 
 }
 
 
}
StatTabs::init();
//var_dump(StatTabs::$statLookup);

?>