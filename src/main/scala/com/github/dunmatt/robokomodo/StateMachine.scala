package com.github.dunmatt.robokomodo

class StateMachine[S](startState: S, actions: (S, S)=>Unit) {
  protected var _state = startState

  def state = _state

  def state_=(s: S) = {
    if (s != state) {
      actions(state, s)
      _state = s
    }
  }
}
