package com.github.dunmatt.robokomodo

class StateMachine[S](startState: S, allowedTransition: (S, S)=>Boolean, actions: (S, S)=>Unit) {
  protected var _state = startState

  def state = _state

  def state_=(s: S) = {
    if (s != state && allowedTransition(state, s)) {
      actions(state, s)
      _state = s
    }
  }
}
