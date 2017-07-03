member(X,[X|_]).
member(X,[_|Xs]):-member(X,Xs).
S:member(X,[X|_]).
S:member(X,[_|Xs]):-S:member(X,Xs).

