$(document).ready(function() {
  var room = $('[name="room"]').val();
  var formPassword = $('[name="formPassword"]').val();
  var element = $('[name="message"]');
  var msgPane = $('#messages-pane');

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

  function refreshParticipantsList() {
    $.get('/n2n-chat/display/', { 'room' : room, 'participantsList' : 'only' }, function(data) {
      $('#participants-list').html(data);
    });
  }

  function refreshMessagePane() {
    //If at the bottom, load new content, then scroll down to the new bottom.
    if (atBottom()) {
      $.get('/n2n-chat/display/', { 'room' : room, 'messagesPane' : 'only' }, function(data) {
        msgPane.html(data);
        msgPane.animate({scrollTop: msgPane[0].scrollHeight});
      });
    } else {
       $.get('/n2n-chat/display/', { 'room' : room, 'messagesPane' : 'only' }, function(data) {
        msgPane.html(data);
      });
    }
  }

  function atBottom() {
      //Total height of scrollable element - visible height - height scrollable area hidden above.
      //If this is less than 0,  it's scrolled down to the bottom line.
      return msgPane[0].scrollHeight - msgPane.height() - msgPane.scrollTop() < 0;
  }

  function refreshPanes() {
    refreshMessagePane();
    refreshParticipantsList();
    setTimeout(refreshPanes, 1000);
  }

  refreshPanes();
});