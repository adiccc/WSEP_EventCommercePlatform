package domain.user;

public class State {
    private State state;
    public State(){
        state=null;
    }
    public void changeState(State state){
        this.state=state;
    }
}
