$(function(){
	
				// Tabs
				var $tabs = $('#tabs').tabs({
					selected: -1
				});
				reloadTabs();
				//hover states on the static widgets
				$('#dialog_link, ul#icons li').hover(
					function() { $(this).addClass('ui-state-hover'); }, 
					function() { $(this).removeClass('ui-state-hover'); }
				);
				
				$('td#choices > :checkbox').click(function() {
					reloadTabs();
				});
				
				function reloadTabs() {
					$('td#choices > input[type=checkbox]:not(:checked)').each(
  						function() {
   							$tabs.tabs( 'disable', parseInt(this.id));
  						}
					);
					$('td#choices > input[type=checkbox]:checked').each(
  						function() {
   						$tabs.tabs('enable', parseInt(this.id));
  						}
					);
				}
			});