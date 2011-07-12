//Values needed for any room that will not change for that room.
var room;
var formPassword;

function refreshPanes() {
  //TODO: Scroll down to new bottom if user is currently at the bottom, otherwise restore the position.
  refreshMessagePane();
  refreshParticipantsList();
  setTimeout(refreshPanes, 1000);
}

function refreshParticipantsList() {
  $.get('/n2n-chat/display/', { 'room' : room, 'participantsList' : 'only' }, function(data) {
    $('#participants-list').html(data);
  });
}

function refreshMessagePane() {
  $.get('/n2n-chat/display/', { 'room' : room, 'messagesPane' : 'only' }, function(data) {
    $('#messages-pane').html(data);
  });
}

$(document).ready(function() {
  room = $('[name="room"]').val();
  formPassword = $('[name="formPassword"]').val();
  var element = $('[name="message"]');
  element.keydown(function(event) {
    if (event.which == 13) {
      //As this is being POSTed manually, suppress the form submitting again, which would submit twice.
      event.preventDefault();
      //Maintain focus on the text field.
      element.focus();
      var message = element.val();
      $.post('/n2n-chat/display/', { 'room' : room, 'message' : message, 'formPassword' : formPassword }, function() {
        element.val("");
        refreshPanes();
      });
     }
    });
  refreshPanes();
});
