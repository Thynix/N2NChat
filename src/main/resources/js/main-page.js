function refreshInvitationTable() {
  $.get('/n2n-chat/main-page/', { 'invitationTable' : 'only' }, function(data, status, jqXHR) {
    //Only update if there was a change.
    if (jqXHR.status == 200) {
      $('#invitationContainer').html(data);
    }
  });
  setTimeout(refreshInvitationTable, 1000);
}

$(document).ready(function() {
    refreshInvitationTable();
});
