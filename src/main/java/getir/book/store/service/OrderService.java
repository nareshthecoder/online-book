package getir.book.store.service;

import getir.book.store.dto.Response;
import getir.book.store.entities.Book;
import getir.book.store.entities.Customer;
import getir.book.store.entities.Order;
import getir.book.store.exception.RecordNotFoundException;
import getir.book.store.repositories.BookRepository;
import getir.book.store.repositories.CustomerRepository;
import getir.book.store.repositories.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@Slf4j
public class OrderService<bookSet> {


    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private final Lock readLock = readWriteLock.readLock();

    private final Lock writeLock = readWriteLock.writeLock();


    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BookRepository bookRepository;

    private ConcurrentHashMap<Long,Book> hashMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init(){

        List<Book> books = bookRepository.findAll();

        for (Book book:books  ) {
            hashMap.put(book.getBookId(),book);
        }

    }


    public Response<Order> placeOrder(Order order){
        try {
       
            Optional<Customer> customer = customerRepository.findById(order.getCustomer().getId());
            validateCustomer(order, customer);
            validateBooks(order);

            order = orderRepository.save(order);
            return Response.<Order>builder().data(order).message("order is created successfully ").statusCode("200").build();
        } catch (Exception e) {
            log.error("Exception while creating order ",e);
            // need to use status code
            return Response.<Order>builder().message("order creation is failed ").statusCode("500").build();
        }
    }

    private void validateBooks(Order order) {

        Set<Book> bookSet = new HashSet<>();

        for (Book book : order.getBooks()) {


            Book b;
            readLock.lock();
            try {

                b = bookRepository.findById(book.getBookId()).get();
                if(b.getIsAvailable()){
                    try {
                        writeLock.lock();
                        b.setIsAvailable(false);
                        bookRepository.flush();
                    } finally {
                        writeLock.unlock();
                    }
                }

            } finally {
                readLock.unlock();

            }

            // check if book is available
          /*  Book b = hashMap.get(book.getBookId());
            if (b != null && !b.getIsAvailable()) {
                log.error(" book is ready booked or not available stock ");
                throw new RecordNotFoundException("book is not available ");
            }
            // to update book status after book
            b.setIsAvailable(false);*/
            bookSet.add(b);

        }
        order.setBooks(bookSet);
    }

    private void validateCustomer(Order order, Optional<Customer> customer) {
        if(customer.isPresent()){
            order.setCustomer(customer.get());
        }
        else{
            throw new RecordNotFoundException("customer is not found");
        }
    }

    public Response<Order> findByOrderDate(Order order){

        try {
            Optional<Order> customer1 = orderRepository.findById(order.getId());
            log.info(" updating  order",order);
            if(customer1.isEmpty()){
                return Response.<Order>builder().statusCode("200").message("order is not exist").build();
            }
            orderRepository.save(order);
            return Response.<Order>builder().data(order).message(" Order is updated Successfully  ").statusCode("200").build();
        } catch (Exception e) {
            log.error("Exception while updating order ",e);
            // need to use status code
            return Response.<Order>builder().message("order update is failed ").statusCode("500").build();
        }
    }

    public Response<List<Order>> findByOrderDate(String startDate, String endDAte){

        try {
            DateFormat  dateFormat = new SimpleDateFormat("dd/MM/yyyy");
            Date date = new Date(dateFormat.parse(startDate).getTime()) ;
            Date date1 = new Date(dateFormat.parse(endDAte).getTime());

            List<Order> list = orderRepository.findByOrderDateBetween(date,date1);
            log.info(" order size() ",list.size());

            return Response.<List<Order>>builder().data(list).message("Sucessfully Registeted ").statusCode("200").build();
        } catch (Exception e) {
            log.error("Exception while un registering order ",e);
            // need to use status code
            return Response.<List<Order>>builder().message("Registration failed ").statusCode("500").build();
        }
    }






    @Autowired
    public void setOrderRepository(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
}
