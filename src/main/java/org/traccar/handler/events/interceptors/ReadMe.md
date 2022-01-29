# What are interceptors?

Interceptors are classes that are invoked when an event handler is invoked
but before it´s execution.



[pipeline] -> Event Handler -> Call interceptors -> execute event handler -> [continue pipeline]

Interceptors must be instantiated inside MainModule.java and added to the respective handler