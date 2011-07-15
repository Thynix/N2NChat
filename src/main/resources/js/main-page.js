function refreshInvitationTable() {
  $.get('/n2n-chat/main-page/', { 'invitationTable' : 'only' }, function(data) {
    $('#invitationContainer').html(data);
  });
  setTimeout(refreshInvitationTable, 1000);
}

$(document).ready(function() {
    refreshInvitationTable();
});
